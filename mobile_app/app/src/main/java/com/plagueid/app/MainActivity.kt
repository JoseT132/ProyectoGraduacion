package com.plagueid.app

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
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.plagueid.app.api.ApiService
import com.plagueid.app.databinding.ActivityMainBinding
import com.plagueid.app.ml.Detection
import com.plagueid.app.ml.OnnxClassifier
import com.plagueid.app.ml.OnnxDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var classifier: OnnxClassifier
    private lateinit var detector: OnnxDetector

    private var currentPhotoUri: Uri? = null
    private var selectedBitmap: Bitmap? = null
    private var lastSlug: String? = null
    private var lastCommonName: String? = null

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
            Toast.makeText(this, R.string.camera_permission, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        classifier = OnnxClassifier(this)
        detector = OnnxDetector(this)

        binding.cameraButton.setOnClickListener { checkCameraPermission() }
        binding.galleryButton.setOnClickListener { galleryLauncher.launch("image/*") }
        binding.identifyButton.setOnClickListener { identify() }
        binding.fichaButton.setOnClickListener { loadFicha() }
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED -> launchCamera()

            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                Toast.makeText(this, R.string.camera_permission, Toast.LENGTH_LONG).show()
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }

            else -> permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val file = createImageFile()
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )
        currentPhotoUri = uri
        cameraLauncher.launch(uri)
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun loadImage(uri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            selectedBitmap = bitmap
            binding.imagePreview.setImageBitmap(bitmap)
            binding.resultText.text = getString(R.string.hint)
            binding.fichaButton.visibility = View.GONE
            lastSlug = null
            lastCommonName = null
        } catch (e: Exception) {
            Toast.makeText(this, R.string.load_image_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun identify() {
        val bitmap = selectedBitmap ?: return Toast.makeText(
            this,
            R.string.select_image_first,
            Toast.LENGTH_SHORT
        ).show()

        binding.progressBar.visibility = View.VISIBLE
        binding.fichaButton.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.Default) {
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
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
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
        lifecycleScope.launch {
            val species = ApiService.fetchSpecies(slug)
            if (species != null) {
                showFichaDialog(species)
            } else {
                Toast.makeText(this@MainActivity, R.string.ficha_error, Toast.LENGTH_SHORT).show()
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

        AlertDialog.Builder(this)
            .setTitle(species.scientificName)
            .setMessage(message)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun Float.format(digits: Int): String {
        return String.format(Locale.getDefault(), "%.${digits}f", this)
    }

    override fun onDestroy() {
        super.onDestroy()
        classifier.close()
        detector.close()
    }
}
