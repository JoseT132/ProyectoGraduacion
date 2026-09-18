package com.plagueid.app.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.plagueid.app.R
import com.plagueid.app.api.DetectionRecord

class DetectionAdapter(private val items: List<DetectionRecord>) :
    RecyclerView.Adapter<DetectionAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val speciesText: TextView = itemView.findViewById(R.id.speciesText)
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
        holder.speciesText.text = item.species?.scientificName ?: "Especie desconocida"
        holder.confidenceText.text = "Confianza: ${"%.2f".format((item.confidence ?: 0f) * 100)}%"
        holder.locationText.text = when {
            item.latitude == null || item.longitude == null -> "Ubicación: no registrada"
            item.inExpectedRange == false ->
                "Ubicación: ${"%.4f".format(item.latitude)}, ${"%.4f".format(item.longitude)} — fuera de rango conocido"
            else ->
                "Ubicación: ${"%.4f".format(item.latitude)}, ${"%.4f".format(item.longitude)}${item.region?.let { " ($it)" } ?: ""}"
        }
        holder.dateText.text = item.createdAt ?: "—"
    }

    override fun getItemCount(): Int = items.size
}
