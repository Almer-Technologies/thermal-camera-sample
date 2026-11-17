package com.realwear.thermalsample.thermal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import java.util.Locale

/**
 * Draws the crosshair and the temperature at the centre into the frame itself.
 *
 * Draw into the bitmap, not only in the UI, when the frame leaves the device: a video call, a
 * recording or a stream. The remote side then sees the reading too.
 */
object TemperatureOverlay {

    fun draw(bitmap: Bitmap, celsius: Double?, fahrenheit: Double?): Bitmap {
        val target = if (bitmap.isMutable) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(target)
        val centerX = target.width / 2f
        val centerY = target.height / 2f
        val size = target.width * CROSSHAIR_SIZE_RATIO

        for (paint in listOf(OUTLINE, FILL)) {
            canvas.drawLine(centerX - size, centerY, centerX + size, centerY, paint)
            canvas.drawLine(centerX, centerY - size, centerX, centerY + size, paint)
        }

        val textSize = target.width * TEXT_SIZE_RATIO
        celsius?.let { drawText(canvas, format(it, "°C"), centerX + size * 1.4f, centerY, textSize, alignRight = false) }
        fahrenheit?.let { drawText(canvas, format(it, "°F"), centerX - size * 1.4f, centerY, textSize, alignRight = true) }
        return target
    }

    private fun drawText(canvas: Canvas, text: String, x: Float, y: Float, textSize: Float, alignRight: Boolean) {
        OUTLINE.textSize = textSize
        FILL.textSize = textSize
        val bounds = Rect().also { FILL.getTextBounds(text, 0, text.length, it) }
        val left = if (alignRight) x - FILL.measureText(text) else x
        val baseline = y + bounds.height() / 2f
        canvas.drawText(text, left, baseline, OUTLINE)
        canvas.drawText(text, left, baseline, FILL)
    }

    private fun format(value: Double, unit: String) = String.format(Locale.US, "%.1f %s", value, unit)

    private const val CROSSHAIR_SIZE_RATIO = 0.04f
    private const val TEXT_SIZE_RATIO = 0.035f

    // Paint is not thread safe. Frames arrive one at a time on the SDK thread, so sharing is fine.
    private val OUTLINE = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = 10f
        style = Paint.Style.STROKE
        typeface = Typeface.DEFAULT_BOLD
    }
    private val FILL = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 4f
        style = Paint.Style.FILL_AND_STROKE
        typeface = Typeface.DEFAULT_BOLD
    }
}
