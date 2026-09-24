package com.plagueid.app.history

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.plagueid.app.R
import com.plagueid.app.SpeciesActivity
import com.plagueid.app.api.DetectionRecord
import com.plagueid.app.util.formatIso

class DetectionAdapter(
    private val items: List<DetectionRecord>,
    private val onDelete: (DetectionRecord) -> Unit
) : RecyclerView.Adapter<DetectionAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val statusStrip: View = itemView.findViewById(R.id.statusStrip)
        val speciesText: TextView = itemView.findViewById(R.id.speciesText)
        val fichaHint: TextView = itemView.findViewById(R.id.fichaHint)
        val deleteButton: TextView = itemView.findViewById(R.id.deleteButton)
        val confidenceText: TextView = itemView.findViewById(R.id.confidenceText)
        val locationText: TextView = itemView.findViewById(R.id.locationText)
        val dateText: TextView = itemView.findViewById(R.id.dateText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_detection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context

        val hasSpecies = item.species != null
        holder.speciesText.text = item.species?.scientificName ?: ctx.getString(R.string.unknown_species)
        holder.confidenceText.text = "Confianza: ${"%.2f".format((item.confidence ?: 0f) * 100)}%"

        holder.locationText.text = when {
            item.latitude == null || item.longitude == null -> "Ubicación: no registrada"
            item.inExpectedRange == false ->
                "Ubicación: ${"%.4f".format(item.latitude)}, ${"%.4f".format(item.longitude)} — fuera de rango conocido"
            else ->
                "Ubicación: ${"%.4f".format(item.latitude)}, ${"%.4f".format(item.longitude)}${item.region?.let { " ($it)" } ?: ""}"
        }

        holder.dateText.text = formatIso(item.createdAt)

        val stripColor = when {
            !hasSpecies -> R.color.status_gray
            item.inExpectedRange == false -> R.color.accent_amber
            else -> R.color.leaf_green
        }
        holder.statusStrip.setBackgroundColor(ContextCompat.getColor(ctx, stripColor))

        holder.fichaHint.visibility = if (hasSpecies) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener {
            val slug = item.species?.slug ?: return@setOnClickListener
            ctx.startActivity(
                Intent(ctx, SpeciesActivity::class.java)
                    .putExtra(SpeciesActivity.EXTRA_SLUG, slug)
            )
        }
        holder.deleteButton.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount(): Int = items.size
}
