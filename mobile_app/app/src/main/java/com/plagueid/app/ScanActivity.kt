package com.plagueid.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.plagueid.app.api.ApiClient
import com.plagueid.app.api.PredictedItem
import com.plagueid.app.data.LocalDetection
import com.plagueid.app.data.LocalDetections
import com.plagueid.app.databinding.ActivityScanBinding
import com.plagueid.app.ml.Detection
import com.plagueid.app.ml.OnnxClassifier
import com.plagueid.app.ml.OnnxDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanBinding
    private lateinit var detector: OnnxDetector
    private lateinit var classifier: OnnxClassifier
    private lateinit var cameraExecutor: ExecutorService

    private val analyzing = AtomicBoolean(false)

    @Volatile private var lastFrame: Bitmap? = null
    @Volatile private var lastDetection: Detection? = null
    @Volatile private var lastPredictions: List<Pair<String, Float>>? = null
    @Volatile private var lastSlug: String? = null

    private val UNKNOWN_THRESHOLD = 0.80f

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else {
            Toast.makeText(this, R.string.camera_permission, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        detector = OnnxDetector(this)
        classifier = OnnxClassifier(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.closeButton.setOnClickListener { finish() }
        binding.captureButton.setOnClickListener { captureDetection() }
        binding.liveLabel.setOnClickListener {
            lastSlug?.let { slug ->
                startActivity(
                    android.content.Intent(this, SpeciesActivity::class.java)
                        .putExtra(SpeciesActivity.EXTRA_SLUG, slug)
                )
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                analyzeFrame(imageProxy)
            }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Error al iniciar cámara: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeFrame(imageProxy: ImageProxy) {
        if (analyzing.getAndSet(true)) {
            imageProxy.close()
            return
        }

        try {
            val rotation = imageProxy.imageInfo.rotationDegrees
            val rawBitmap = imageProxy.toBitmap()
            imageProxy.close()

            val frame = if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            } else rawBitmap

            val detection = detector.detect(frame, confThreshold = 0.25f)

            if (detection == null) {
                lastDetection = null
                lastPredictions = null
                lastSlug = null
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        binding.overlay.clear()
                        binding.liveLabel.text = getString(R.string.scanning)
                        binding.captureButton.isEnabled = false
                    }
                }
                analyzing.set(false)
                return
            }

            val crop = cropBitmap(frame, detection)
            val predictions = classifier.classify(crop)
            val top = predictions.first()
            val isUnknown = top.second < UNKNOWN_THRESHOLD
            val slug = if (isUnknown) null else top.first

            lastFrame = frame
            lastDetection = detection
            lastPredictions = predictions
            lastSlug = slug

            val labelText = if (isUnknown) {
                "${getString(R.string.unknown_entry)} · ${(top.second * 100).format(1)}%"
            } else {
                "${top.first.replace("_", " ")} · ${(top.second * 100).format(1)}%"
            }

            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    binding.overlay.update(detection, labelText, frame.width, frame.height)
                    binding.liveLabel.text = labelText
                    binding.captureButton.isEnabled = true
                }
            }
        } catch (e: Exception) {
            imageProxy.close()
        } finally {
            analyzing.set(false)
        }
    }

    private fun captureDetection() {
        val frame = lastFrame
        val detection = lastDetection
        val predictions = lastPredictions

        if (frame == null || detection == null || predictions == null) {
            Toast.makeText(this, R.string.no_detection_to_save, Toast.LENGTH_SHORT).show()
            return
        }

        val top = predictions.first()
        val isUnknown = top.second < UNKNOWN_THRESHOLD
        val slug = if (isUnknown) null else top.first
        val location = getLastLocation()
        val predictedItems = predictions.mapIndexed { i, p ->
            PredictedItem(rank = i + 1, species = p.first.replace("_", " "), slug = p.first, confidence = p.second)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val (record, _) = LocalDetections.newRecord(
                source = "live",
                box = detection,
                topSlug = slug,
                topConfidence = top.second,
                topPredictions = predictedItems,
                latitude = location?.latitude,
                longitude = location?.longitude
            )
            LocalDetections.save(applicationContext, frame, record)

            val crop = cropBitmap(frame, detection)
            val synced = syncToBackend(crop, record.id)

            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext
                Toast.makeText(
                    this@ScanActivity,
                    if (synced) R.string.saved_synced else R.string.saved_local,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private suspend fun syncToBackend(bitmap: Bitmap, localId: String): Boolean {
        return try {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
            val requestBody = baos.toByteArray().toRequestBody("image/jpeg".toMediaType())
            val part = MultipartBody.Part.createFormData("file", "detection.jpg", requestBody)
            val location = getLastLocation()
            val response = ApiClient.getApi(applicationContext).predict(
                part,
                latitude = location?.latitude,
                longitude = location?.longitude
            )
            val body = response.body()
            if (response.isSuccessful && body != null) {
                LocalDetections.markSynced(
                    applicationContext, localId, body.detectionId, body.region, body.inExpectedRange
                )
                true
            } else {
                LocalDetections.markSyncError(applicationContext, localId, "HTTP ${response.code()}")
                false
            }
        } catch (e: Exception) {
            LocalDetections.markSyncError(applicationContext, localId, e.message ?: "error")
            false
        }
    }

    private fun getLastLocation(): Location? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
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

    private fun Float.format(digits: Int): String =
        String.format(java.util.Locale.getDefault(), "%.${digits}f", this)

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        if (::detector.isInitialized) detector.close()
        if (::classifier.isInitialized) classifier.close()
    }
}
