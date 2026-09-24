package com.plagueid.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DISPLAY = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

fun formatIso(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    return try {
        val base = iso.substringBefore('.').substringBefore('+').replace('Z', ' ')
            .trim()
        ISO.parse(base)?.let { DISPLAY.format(it) } ?: iso
    } catch (e: Exception) {
        iso
    }
}

fun formatEpoch(millis: Long): String = DISPLAY.format(Date(millis))
