package com.plagueid.app

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.plagueid.app.ml.OnnxClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var classifier: OnnxClassifier

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
            Toast.makeText(this, R.string.camera_permission, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        classifier = OnnxClassifier(this)

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
        } catch (e: Exception) {
            Toast.makeText(this, "Error al cargar imagen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun identify() {
        val bitmap = selectedBitmap ?: return Toast.makeText(
            this,
            "Selecciona o toma una foto primero",
            Toast.LENGTH_SHORT
        ).show()

        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val predictions = classifier.classify(bitmap)
                val top = predictions.first()
                lastSlug = top.first

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.resultText.text = buildString {
                        append("${top.first.replace("_", " ")}\n")
                        append("Confianza: ${(top.second * 100).format(2)}%\n\n")
                        append("Top 5:\n")
                        predictions.forEachIndexed { i, p ->
                            append("${i + 1}. ${p.first.replace("_", " ")}: ${(p.second * 100).format(2)}%\n")
                        }
                    }
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

    private fun loadFicha() {
        val slug = lastSlug ?: return
        lifecycleScope.launch {
            val species = ApiService.fetchSpecies(slug)
            if (species != null) {
                showFichaDialog(species)
            } else {
                Toast.makeText(this@MainActivity, "No se pudo cargar la ficha técnica", Toast.LENGTH_SHORT).show()
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
    }
}
