package com.plagueid.app.api

import android.util.Log
import com.google.gson.Gson
import com.plagueid.app.Species
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object ApiService {

    // Ajusta a la IP de tu servidor FastAPI cuando uses un dispositivo físico.
    private const val BASE_URL = "http://10.0.2.2:8000"

    suspend fun fetchSpecies(slug: String): Species? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/species/$slug")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                return@withContext Gson().fromJson(response, Species::class.java)
            }
        } catch (e: Exception) {
            Log.e("ApiService", "Error fetching species", e)
        }
        null
    }
}
