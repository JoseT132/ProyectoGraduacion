package com.plagueid.app.ml

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var detection: Detection? = null
    private var label: String? = null
    private var sourceWidth = 0
    private var sourceHeight = 0

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8BC34A")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val labelBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC263238")
        style = Paint.Style.FILL
    }

    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
        isFakeBoldText = true
    }

    fun update(det: Detection?, text: String?, srcWidth: Int, srcHeight: Int) {
        detection = det
        label = text
        sourceWidth = srcWidth
        sourceHeight = srcHeight
        invalidate()
    }

    fun clear() {
        detection = null
        label = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val det = detection ?: return
        if (sourceWidth <= 0 || sourceHeight <= 0 || width <= 0 || height <= 0) return

        // FIT_CENTER: mismo escalado que PreviewView con scaleType="fitCenter"
        val scale = minOf(width / sourceWidth.toFloat(), height / sourceHeight.toFloat())
        val offsetX = (width - sourceWidth * scale) / 2f
        val offsetY = (height - sourceHeight * scale) / 2f

        val rect = RectF(
            offsetX + det.x1 * scale,
            offsetY + det.y1 * scale,
            offsetX + det.x2 * scale,
            offsetY + det.y2 * scale
        )
        canvas.drawRect(rect, boxPaint)

        val text = label ?: return
        val padding = 16f
        val textWidth = labelTextPaint.measureText(text)
        val textHeight = labelTextPaint.descent() - labelTextPaint.ascent()

        var labelTop = rect.top - textHeight - padding * 2
        if (labelTop < 0) labelTop = rect.bottom + 4f

        val bgRect = RectF(
            rect.left,
            labelTop,
            rect.left + textWidth + padding * 2,
            labelTop + textHeight + padding * 2
        )
        canvas.drawRoundRect(bgRect, 10f, 10f, labelBackgroundPaint)
        canvas.drawText(
            text,
            bgRect.left + padding,
            bgRect.top + padding - labelTextPaint.ascent(),
            labelTextPaint
        )
    }
}
