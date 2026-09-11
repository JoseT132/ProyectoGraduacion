package com.plagueid.app.home

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.plagueid.app.R
import com.plagueid.app.Species
import com.plagueid.app.api.ApiClient
import com.plagueid.app.databinding.FragmentHomeBinding
import com.plagueid.app.ml.Detection
import com.plagueid.app.ml.OnnxClassifier
import com.plagueid.app.ml.OnnxDetector
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var classifier: OnnxClassifier
    private lateinit var detector: OnnxDetector

    private var currentPhotoUri: Uri? = null
    private var selectedBitmap: Bitmap? = null
    private var lastSlug: String? = null

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { loadImage(it) }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            currentPhotoUri?.let { loadImage(it) }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera() else
            Toast.makeText(requireContext(), R.string.camera_permission, Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        classifier = OnnxClassifier(requireContext())
        detector = OnnxDetector(requireContext())

        binding.cameraButton.setOnClickListener { checkCameraPermission() }
        binding.galleryButton.setOnClickListener { galleryLauncher.launch("image/*") }
        binding.identifyButton.setOnClickListener { identify() }
        binding.fichaButton.setOnClickListener { loadFicha() }
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED -> launchCamera()

            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                Toast.makeText(requireContext(), R.string.camera_permission, Toast.LENGTH_LONG).show()
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }

            else -> permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val file = createImageFile()
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )
        currentPhotoUri = uri
        cameraLauncher.launch(uri)
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun loadImage(uri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
            selectedBitmap = bitmap
            binding.imagePreview.setImageBitmap(bitmap)
            binding.resultText.text = getString(R.string.hint)
            binding.fichaButton.visibility = View.GONE
            lastSlug = null
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.load_image_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun identify() {
        val bitmap = selectedBitmap ?: return Toast.makeText(
            requireContext(),
            R.string.select_image_first,
            Toast.LENGTH_SHORT
        ).show()

        binding.progressBar.visibility = View.VISIBLE
        binding.fichaButton.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            try {
                val detection = detector.detect(bitmap, confThreshold = 0.25f)

                if (detection == null) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        binding.resultText.text = getString(R.string.no_insect_detected)
                    }
                    return@launch
                }

                val crop = cropBitmap(bitmap, detection)
                val predictions = classifier.classify(crop)
                val top = predictions.first()
                lastSlug = top.first

                sendDetectionToBackend(crop)

                val overlay = drawDetection(bitmap, detection)
                val message = buildString {
                    appendLine("Especie: ${top.first.replace("_", " ")}")
                    appendLine("Confianza: ${(top.second * 100).format(2)}%")
                    appendLine()
                    appendLine("Detección: ${(detection.confidence * 100).format(2)}%")
                    appendLine("Top 5:")
                    predictions.forEachIndexed { i, p ->
                        appendLine("${i + 1}. ${p.first.replace("_", " ")}: ${(p.second * 100).format(2)}%")
                    }
                }

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.imagePreview.setImageBitmap(overlay)
                    binding.resultText.text = message
                    binding.fichaButton.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun sendDetectionToBackend(bitmap: Bitmap) {
        val appContext = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                val requestBody = RequestBody.create("image/jpeg".toMediaType(), baos.toByteArray())
                val part = MultipartBody.Part.createFormData("file", "detection.jpg", requestBody)
                ApiClient.getApi(appContext).predict(part)
            } catch (e: Exception) {
                // Si el backend no está disponible, no bloqueamos la experiencia local.
            }
        }
    }

    private fun cropBitmap(bitmap: Bitmap, detection: Detection): Bitmap {
        val x = detection.x1.toInt().coerceAtLeast(0)
        val y = detection.y1.toInt().coerceAtLeast(0)
        val width = (detection.x2 - detection.x1).toInt().coerceAtLeast(1)
        val height = (detection.y2 - detection.y1).toInt().coerceAtLeast(1)
        val safeWidth = width.coerceAtMost(bitmap.width - x)
        val safeHeight = height.coerceAtMost(bitmap.height - y)
        return Bitmap.createBitmap(bitmap, x, y, safeWidth, safeHeight)
    }

    private fun drawDetection(bitmap: Bitmap, detection: Detection): Bitmap {
        val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)
        val paint = Paint().apply {
            color = android.graphics.Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 8f
            isAntiAlias = true
        }
        val rect = Rect(
            detection.x1.toInt().coerceAtLeast(0),
            detection.y1.toInt().coerceAtLeast(0),
            detection.x2.toInt().coerceAtMost(bitmap.width),
            detection.y2.toInt().coerceAtMost(bitmap.height)
        )
        canvas.drawRect(rect, paint)
        return mutable
    }

    private fun loadFicha() {
        val slug = lastSlug ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.getApi(requireContext()).getSpecies(slug)
                }
                val species = response.body()
                if (species != null) {
                    showFichaDialog(species)
                } else {
                    Toast.makeText(requireContext(), R.string.ficha_error, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showFichaDialog(species: Species) {
        val message = buildString {
            appendLine("Nombre común: ${species.commonName ?: "—"}")
            appendLine("Familia: ${species.family ?: "—"}")
            appendLine()
            appendLine("Descripción: ${species.description ?: "—"}")
            appendLine()
            appendLine("Daños: ${species.damage ?: "—"}")
            appendLine()
            appendLine("Control biológico: ${species.biologicalControl ?: "—"}")
            appendLine("Control cultural: ${species.culturalControl ?: "—"}")
            appendLine("Control químico: ${species.chemicalControl ?: "—"}")
            appendLine("Umbral: ${species.threshold ?: "—"}")
        }

        AlertDialog.Builder(requireContext())
            .setTitle(species.scientificName)
            .setMessage(message)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun Float.format(digits: Int): String {
        return String.format(Locale.getDefault(), "%.${digits}f", this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::classifier.isInitialized) classifier.close()
        if (::detector.isInitialized) detector.close()
    }
}
