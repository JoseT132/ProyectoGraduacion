package com.plagueid.app.ml

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OnnxTensor
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer

data class Detection(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val confidence: Float
)

class OnnxDetector(context: Context) {

    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputSize = 640
    private val numAnchors = 8400

    init {
        val modelFile = copyAsset(context, "insect_detector.onnx")
        session = environment.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
    }

    fun detect(bitmap: Bitmap, confThreshold: Float = 0.25f): Detection? {
        val (tensor, origWidth, origHeight) = preprocess(bitmap)

        val results = session.run(mapOf(session.inputNames.first() to tensor))
        val outputTensor = results.get(0) as OnnxTensor
        val output = outputTensor.getValue() as? Array<Array<FloatArray>>
            ?: throw IllegalStateException("Unexpected output shape")

        val xArr = output[0][0]
        val yArr = output[0][1]
        val wArr = output[0][2]
        val hArr = output[0][3]
        val sArr = output[0][4]

        val candidates = mutableListOf<Detection>()
        for (i in 0 until numAnchors) {
            val score = sArr[i]
            if (score < confThreshold) continue

            val cx = xArr[i]
            val cy = yArr[i]
            val w = wArr[i]
            val h = hArr[i]

            val x1 = ((cx - w / 2) / inputSize * origWidth).coerceIn(0f, origWidth.toFloat())
            val y1 = ((cy - h / 2) / inputSize * origHeight).coerceIn(0f, origHeight.toFloat())
            val x2 = ((cx + w / 2) / inputSize * origWidth).coerceIn(0f, origWidth.toFloat())
            val y2 = ((cy + h / 2) / inputSize * origHeight).coerceIn(0f, origHeight.toFloat())

            candidates.add(Detection(x1, y1, x2, y2, score))
        }

        tensor.close()
        results.close()

        if (candidates.isEmpty()) return null

        candidates.sortByDescending { it.confidence }

        val selected = mutableListOf<Detection>()
        for (box in candidates) {
            if (selected.none { iou(box, it) > 0.7f }) {
                selected.add(box)
                if (selected.size >= 3) break
            }
        }

        return selected.firstOrNull()
    }

    private fun preprocess(bitmap: Bitmap): Triple<OnnxTensor, Int, Int> {
        val origWidth = bitmap.width
        val origHeight = bitmap.height
        val scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val pixels = IntArray(inputSize * inputSize)
        scaled.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        val floatBuffer = FloatBuffer.allocate(1 * 3 * inputSize * inputSize)
        for (c in 0..2) {
            for (y in 0 until inputSize) {
                for (x in 0 until inputSize) {
                    val pixel = pixels[y * inputSize + x]
                    val value = when (c) {
                        0 -> (pixel shr 16 and 0xFF) / 255.0f
                        1 -> (pixel shr 8 and 0xFF) / 255.0f
                        else -> (pixel and 0xFF) / 255.0f
                    }
                    floatBuffer.put(value)
                }
            }
        }
        floatBuffer.rewind()

        val tensor = OnnxTensor.createTensor(
            environment,
            floatBuffer,
            longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
        )
        return Triple(tensor, origWidth, origHeight)
    }

    private fun iou(a: Detection, b: Detection): Float {
        val xLeft = maxOf(a.x1, b.x1)
        val yTop = maxOf(a.y1, b.y1)
        val xRight = minOf(a.x2, b.x2)
        val yBottom = minOf(a.y2, b.y2)

        val interArea = maxOf(0f, xRight - xLeft) * maxOf(0f, yBottom - yTop)
        val areaA = (a.x2 - a.x1) * (a.y2 - a.y1)
        val areaB = (b.x2 - b.x1) * (b.y2 - b.y1)
        val union = areaA + areaB - interArea

        return if (union <= 0f) 0f else interArea / union
    }

    private fun copyAsset(context: Context, assetName: String): File {
        val outFile = File(context.cacheDir, assetName)
        if (!outFile.exists()) {
            context.assets.open(assetName).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return outFile
    }

    fun close() {
        session.close()
    }
}
