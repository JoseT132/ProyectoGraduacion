package com.plagueid.app

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.core.content.ContextCompat
import com.plagueid.app.api.ApiClient
import com.plagueid.app.data.LocalDetections
import com.plagueid.app.util.formatEpoch
import com.plagueid.app.util.formatIso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MapActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(
            this, getSharedPreferences("osmdroid", MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.activity_map)

        emptyText = findViewById(R.id.emptyText)
        map = findViewById(R.id.mapView)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(7.0)
        map.controller.setCenter(GeoPoint(14.6349, -90.5069))

        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }

        loadMarkers()
    }

    private fun loadMarkers() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.getApi(this@MapActivity).getDetections()
                val remote = if (response.isSuccessful) response.body() ?: emptyList() else emptyList()
                val local = LocalDetections.list(this@MapActivity)
                withContext(Dispatchers.Main) {
                    val geoPoints = mutableListOf<GeoPoint>()

                    remote.filter { it.latitude != null && it.longitude != null }.forEach { d ->
                        addMarker(
                            GeoPoint(d.latitude!!, d.longitude!!),
                            d.species?.scientificName ?: getString(R.string.unknown_entry),
                            "${formatIso(d.createdAt)}${d.region?.let { " · $it" } ?: ""}",
                            R.color.leaf_green,
                            geoPoints
                        )
                    }

                    local.filter { it.latitude != null && it.longitude != null }.forEach { d ->
                        addMarker(
                            GeoPoint(d.latitude!!, d.longitude!!),
                            d.topSlug?.replace("_", " ") ?: getString(R.string.unknown_entry),
                            "${formatEpoch(d.createdAt)} · local",
                            R.color.accent_amber,
                            geoPoints
                        )
                    }

                    if (geoPoints.isEmpty()) {
                        emptyText.visibility = View.VISIBLE
                        return@withContext
                    }
                    if (geoPoints.size == 1) {
                        map.controller.setZoom(12.0)
                        map.controller.setCenter(geoPoints[0])
                    } else {
                        val box = BoundingBox.fromGeoPoints(geoPoints)
                        map.zoomToBoundingBox(box, false, 120)
                    }
                    map.invalidate()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MapActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun addMarker(
        point: GeoPoint,
        title: String,
        snippet: String,
        tintRes: Int,
        geoPoints: MutableList<GeoPoint>
    ) {
        geoPoints.add(point)
        val marker = Marker(map)
        marker.position = point
        marker.title = title
        marker.snippet = snippet
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        ContextCompat.getDrawable(this, R.drawable.ic_map_marker)?.let { icon ->
            marker.icon = icon.mutate().apply {
                setTint(ContextCompat.getColor(this@MapActivity, tintRes))
            }
        }
        map.overlays.add(marker)
    }

    override fun onResume() {
        super.onResume()
        if (::map.isInitialized) map.onResume()
    }

    override fun onPause() {
        if (::map.isInitialized) map.onPause()
        super.onPause()
    }
}
