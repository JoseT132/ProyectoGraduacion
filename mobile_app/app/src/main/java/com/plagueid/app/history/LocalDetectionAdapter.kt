package com.plagueid.app.history

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import android.content.Intent
import com.plagueid.app.R
import com.plagueid.app.SpeciesActivity
import com.plagueid.app.data.LocalDetection
import com.plagueid.app.data.LocalDetections
import com.plagueid.app.util.formatEpoch

class LocalDetectionAdapter(
    private val items: List<LocalDetection>,
    private val onDelete: (LocalDetection) -> Unit
) : RecyclerView.Adapter<LocalDetectionAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val thumbImage: ImageView = itemView.findViewById(R.id.thumbImage)
        val speciesText: TextView = itemView.findViewById(R.id.speciesText)
        val dateText: TextView = itemView.findViewById(R.id.dateText)
        val statusText: TextView = itemView.findViewById(R.id.statusText)
        val deleteButton: TextView = itemView.findViewById(R.id.deleteButton)
        val fichaChevron: View = itemView.findViewById(R.id.fichaChevron)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_local_detection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context

        holder.speciesText.text =
            item.topSlug?.replace("_", " ") ?: ctx.getString(R.string.unknown_species)
        holder.dateText.text = formatEpoch(item.createdAt)

        val (statusText, statusColor) = when {
            item.synced -> ctx.getString(R.string.sync_status_synced) to R.color.leaf_green
            !item.syncError.isNullOrBlank() ->
                ctx.getString(R.string.sync_status_error) to R.color.status_error
            else -> ctx.getString(R.string.sync_status_pending) to R.color.accent_amber
        }
        holder.statusText.text = statusText
        holder.statusText.setTextColor(ContextCompat.getColor(ctx, statusColor))

        val file = LocalDetections.imageFile(ctx, item)
        val thumb = if (file.exists()) decodeThumb(file.absolutePath) else null
        if (thumb != null) {
            holder.thumbImage.setImageBitmap(thumb)
        } else {
            holder.thumbImage.setImageResource(R.drawable.ic_leaf_light)
        }

        holder.deleteButton.setOnClickListener { onDelete(item) }

        holder.fichaChevron.visibility = if (item.topSlug != null) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener {
            val slug = item.topSlug ?: return@setOnClickListener
            ctx.startActivity(
                Intent(ctx, SpeciesActivity::class.java)
                    .putExtra(SpeciesActivity.EXTRA_SLUG, slug)
            )
        }
    }

    override fun getItemCount(): Int = items.size

    private fun decodeThumb(path: String): android.graphics.Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 256)
        }
        return BitmapFactory.decodeFile(path, opts)
    }
}
