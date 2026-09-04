package com.plagueid.app.ml

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer

class OnnxClassifier(context: Context) {

    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val labels: List<String>

    init {
        val modelFile = copyAsset(context, "insect_classifier.onnx")
        session = environment.createSession(modelFile.absolutePath, OrtSession.SessionOptions())

        val json = context.assets.open("labels.json").bufferedReader().use { it.readText() }
        val type = object : TypeToken<Map<String, String>>() {}.type
        val map: Map<String, String> = Gson().fromJson(json, type)
        labels = (0 until map.size).map { map[it.toString()] ?: "unknown_$it" }
    }

    fun classify(bitmap: Bitmap): List<Pair<String, Float>> {
        val inputName = session.inputNames.first()
        val outputName = session.outputNames.first()

        val tensor = preprocess(bitmap)
        val results = session.run(mapOf(inputName to tensor))

        val outputTensor = results.get(0) as OnnxTensor
        val output = outputTensor.getValue() as? Array<FloatArray>
            ?: throw IllegalStateException("Unexpected output shape")

        val scores = output[0]
            .mapIndexed { index, value -> labels[index] to value }
            .sortedByDescending { it.second }
            .take(5)

        tensor.close()
        results.close()

        return scores
    }

    private fun preprocess(bitmap: Bitmap): OnnxTensor {
        val size = 224
        val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)

        val floatBuffer = FloatBuffer.allocate(1 * 3 * size * size)
        for (c in 0..2) {
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val pixel = pixels[y * size + x]
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

        return OnnxTensor.createTensor(
            environment,
            floatBuffer,
            longArrayOf(1, 3, size.toLong(), size.toLong())
        )
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
