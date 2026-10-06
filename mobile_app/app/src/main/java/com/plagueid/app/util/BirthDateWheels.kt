package com.plagueid.app.util

import android.view.View
import android.widget.NumberPicker
import com.plagueid.app.R
import java.util.Calendar

/**
 * Tres tambores NumberPicker (día/mes/año) para capturar fecha de nacimiento.
 * Se encarga de rangos, clamp de días según mes/año y formato ISO.
 */
class BirthDateWheels(root: View) {

    private val dayPicker: NumberPicker = root.findViewById(R.id.dayPicker)
    private val monthPicker: NumberPicker = root.findViewById(R.id.monthPicker)
    private val yearPicker: NumberPicker = root.findViewById(R.id.yearPicker)

    private val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    init {
        dayPicker.minValue = 1
        dayPicker.maxValue = 31

        monthPicker.minValue = 1
        monthPicker.maxValue = 12

        yearPicker.minValue = 1900
        yearPicker.maxValue = currentYear

        val clampListener = NumberPicker.OnValueChangeListener { _, _, _ -> clampDay() }
        monthPicker.setOnValueChangedListener(clampListener)
        yearPicker.setOnValueChangedListener(clampListener)

        setDate(1, 1, 2000)
    }

    fun setDate(day: Int, month: Int, year: Int) {
        yearPicker.value = year.coerceIn(1900, currentYear)
        monthPicker.value = month.coerceIn(1, 12)
        clampDay()
        dayPicker.value = day.coerceIn(1, dayPicker.maxValue)
    }

    /** Devuelve la fecha seleccionada en formato yyyy-MM-dd. */
    fun toIsoDate(): String = String.format(
        "%04d-%02d-%02d", yearPicker.value, monthPicker.value, dayPicker.value
    )

    private fun clampDay() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, yearPicker.value)
            set(Calendar.MONTH, monthPicker.value - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        if (dayPicker.maxValue != maxDay) {
            val current = dayPicker.value
            dayPicker.maxValue = maxDay
            if (current > maxDay) dayPicker.value = maxDay
        }
    }
}
