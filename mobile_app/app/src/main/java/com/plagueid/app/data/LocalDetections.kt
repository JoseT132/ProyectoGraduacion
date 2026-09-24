package com.plagueid.app.data

import android.content.Context
import android.graphics.Bitmap
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.plagueid.app.api.PredictedItem
import java.io.File
import java.util.UUID

/**
 * Almacenamiento local persistente de detecciones para futura sincronización.
 * Imágenes como archivos .jpg en filesDir/detections/, metadatos en detections.json.
 */
data class LocalDetection(
    val id: String,
    val imageFile: String,
    val createdAt: Long,
    val source: String,
    val boxX1: Float? = null,
    val boxY1: Float? = null,
    val boxX2: Float? = null,
    val boxY2: Float? = null,
    val detectionConfidence: Float? = null,
    val topSlug: String? = null,
    val topConfidence: Float? = null,
    val topPredictions: List<PredictedItem>? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val region: String? = null,
    val inExpectedRange: Boolean? = null,
    val synced: Boolean = false,
    val remoteDetectionId: Int? = null,
    val syncError: String? = null
)

object LocalDetections {

    private const val DIR_NAME = "detections"
    private const val INDEX_FILE = "detections.json"

    private val gson = Gson()
    private val lock = Any()

    private fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    private fun indexFile(context: Context): File =
        File(dir(context), INDEX_FILE)

    fun save(context: Context, bitmap: Bitmap, record: LocalDetection): LocalDetection {
        synchronized(lock) {
            val imageFile = File(dir(context), record.imageFile)
            imageFile.outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
            }
            val records = list(context).toMutableList()
            records.add(0, record)
            writeIndex(context, records)
            return record
        }
    }

    fun newRecord(
        source: String,
        box: com.plagueid.app.ml.Detection?,
        topSlug: String?,
        topConfidence: Float?,
        topPredictions: List<PredictedItem>?,
        latitude: Double?,
        longitude: Double?
    ): Pair<LocalDetection, String> {
        val id = UUID.randomUUID().toString()
        val imageFile = "det_$id.jpg"
        return LocalDetection(
            id = id,
            imageFile = imageFile,
            createdAt = System.currentTimeMillis(),
            source = source,
            boxX1 = box?.x1,
            boxY1 = box?.y1,
            boxX2 = box?.x2,
            boxY2 = box?.y2,
            detectionConfidence = box?.confidence,
            topSlug = topSlug,
            topConfidence = topConfidence,
            topPredictions = topPredictions,
            latitude = latitude,
            longitude = longitude,
            synced = false
        ) to imageFile
    }

    fun list(context: Context): List<LocalDetection> {
        synchronized(lock) {
            val file = indexFile(context)
            if (!file.exists()) return emptyList()
            return try {
                val type = object : TypeToken<List<LocalDetection>>() {}.type
                gson.fromJson<List<LocalDetection>>(file.readText(), type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun pendingSync(context: Context): List<LocalDetection> =
        list(context).filter { !it.synced }

    fun imageFile(context: Context, record: LocalDetection): File =
        File(dir(context), record.imageFile)

    fun delete(context: Context, localId: String) {
        synchronized(lock) {
            val records = list(context)
            records.firstOrNull { it.id == localId }?.let {
                File(dir(context), it.imageFile).delete()
            }
            writeIndex(context, records.filter { it.id != localId })
        }
    }

    fun markSynced(
        context: Context,
        localId: String,
        remoteId: Int?,
        region: String?,
        inExpectedRange: Boolean?
    ) {
        update(context, localId) {
            it.copy(
                synced = true,
                remoteDetectionId = remoteId,
                region = region ?: it.region,
                inExpectedRange = inExpectedRange ?: it.inExpectedRange,
                syncError = null
            )
        }
    }

    fun markSyncError(context: Context, localId: String, error: String) {
        update(context, localId) { it.copy(syncError = error) }
    }

    private fun update(context: Context, localId: String, transform: (LocalDetection) -> LocalDetection) {
        synchronized(lock) {
            val records = list(context).map {
                if (it.id == localId) transform(it) else it
            }
            writeIndex(context, records)
        }
    }

    private fun writeIndex(context: Context, records: List<LocalDetection>) {
        val tmp = File(dir(context), "$INDEX_FILE.tmp")
        tmp.writeText(gson.toJson(records))
        val target = indexFile(context)
        if (target.exists()) target.delete()
        tmp.renameTo(target)
    }
}
