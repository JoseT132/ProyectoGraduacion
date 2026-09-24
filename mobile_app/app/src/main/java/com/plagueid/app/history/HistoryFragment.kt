package com.plagueid.app.history

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.plagueid.app.MapActivity
import com.plagueid.app.R
import com.plagueid.app.api.ApiClient
import com.plagueid.app.api.DetectionRecord
import com.plagueid.app.data.LocalDetection
import com.plagueid.app.data.LocalDetections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

class HistoryFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var localRecyclerView: RecyclerView
    private lateinit var progressBar: View
    private lateinit var emptyText: View
    private lateinit var localEmptyText: View
    private lateinit var pendingCount: TextView
    private lateinit var syncButton: View
    private lateinit var mapButton: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.recyclerView)
        localRecyclerView = view.findViewById(R.id.localRecyclerView)
        progressBar = view.findViewById(R.id.progressBar)
        emptyText = view.findViewById(R.id.emptyText)
        localEmptyText = view.findViewById(R.id.localEmptyText)
        pendingCount = view.findViewById(R.id.pendingCount)
        syncButton = view.findViewById(R.id.syncButton)
        mapButton = view.findViewById(R.id.mapButton)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        localRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        syncButton.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) { syncPending() }
        }
        mapButton.setOnClickListener {
            startActivity(Intent(requireContext(), MapActivity::class.java))
        }

        loadHistory()
    }

    override fun onResume() {
        super.onResume()
        if (::recyclerView.isInitialized) loadHistory()
    }

    private fun loadHistory() {
        loadLocal()
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.getApi(requireContext()).getDetections()
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (!isAdded) return@withContext
                    if (response.isSuccessful && response.body() != null) {
                        val items = response.body()!!
                        emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                        recyclerView.adapter = DetectionAdapter(items) { confirmDeleteRemote(it) }
                    } else {
                        Toast.makeText(requireContext(), "No se pudo cargar el historial", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (isAdded) {
                        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun loadLocal() {
        val ctx = context ?: return
        val locals = LocalDetections.list(ctx)
        pendingCount.text = getString(R.string.pending_count, locals.count { !it.synced })
        localEmptyText.visibility = if (locals.isEmpty()) View.VISIBLE else View.GONE
        localRecyclerView.adapter = LocalDetectionAdapter(locals) { confirmDeleteLocal(it) }
    }

    private fun confirmDeleteLocal(record: LocalDetection) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_detection_title)
            .setMessage(R.string.delete_local_message)
            .setPositiveButton(R.string.delete_button) { _, _ ->
                LocalDetections.delete(requireContext(), record.id)
                loadLocal()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteRemote(record: DetectionRecord) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_detection_title)
            .setMessage(R.string.delete_remote_message)
            .setPositiveButton(R.string.delete_button) { _, _ -> deleteRemote(record) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteRemote(record: DetectionRecord) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.getApi(requireContext()).deleteDetection(record.id)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        loadHistory()
                    } else {
                        Toast.makeText(requireContext(), R.string.delete_error, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun syncPending() {
        val ctx = requireContext().applicationContext
        val pending = LocalDetections.pendingSync(ctx)
        if (pending.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(ctx, R.string.nothing_to_sync, Toast.LENGTH_SHORT).show()
            }
            return
        }

        withContext(Dispatchers.Main) { syncButton.isEnabled = false }
        var ok = 0
        for (record in pending) {
            try {
                val file = LocalDetections.imageFile(ctx, record)
                val bitmap = if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                if (bitmap == null) {
                    LocalDetections.markSyncError(ctx, record.id, "imagen no encontrada")
                    continue
                }
                val crop = cropFromRecord(bitmap, record)
                val baos = ByteArrayOutputStream()
                crop.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                val body = baos.toByteArray().toRequestBody("image/jpeg".toMediaType())
                val part = MultipartBody.Part.createFormData("file", record.imageFile, body)
                val response = ApiClient.getApi(ctx).predict(
                    part,
                    latitude = record.latitude,
                    longitude = record.longitude
                )
                val predictBody = response.body()
                if (response.isSuccessful && predictBody != null) {
                    LocalDetections.markSynced(
                        ctx, record.id, predictBody.detectionId,
                        predictBody.region, predictBody.inExpectedRange
                    )
                    ok++
                } else {
                    LocalDetections.markSyncError(ctx, record.id, "HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                LocalDetections.markSyncError(ctx, record.id, e.message ?: "error")
            }
        }

        withContext(Dispatchers.Main) {
            syncButton.isEnabled = true
            Toast.makeText(
                ctx,
                getString(R.string.sync_result, ok, pending.size),
                Toast.LENGTH_LONG
            ).show()
            loadHistory()
        }
    }

    private fun cropFromRecord(bitmap: Bitmap, record: LocalDetection): Bitmap {
        val x1 = record.boxX1 ?: return bitmap
        val y1 = record.boxY1 ?: return bitmap
        val x2 = record.boxX2 ?: return bitmap
        val y2 = record.boxY2 ?: return bitmap
        val x = x1.toInt().coerceAtLeast(0)
        val y = y1.toInt().coerceAtLeast(0)
        val w = (x2 - x1).toInt().coerceAtLeast(1).coerceAtMost(bitmap.width - x)
        val h = (y2 - y1).toInt().coerceAtLeast(1).coerceAtMost(bitmap.height - y)
        if (x >= bitmap.width || y >= bitmap.height || w <= 0 || h <= 0) return bitmap
        return Bitmap.createBitmap(bitmap, x, y, w, h)
    }
}
