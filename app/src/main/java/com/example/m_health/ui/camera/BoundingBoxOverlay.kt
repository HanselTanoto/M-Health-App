package com.example.m_health.ui.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.m_health.R
import com.example.m_health.model.data.BoundingBox


/**
 * Custom view for drawing bounding boxes overlay on an image.
 *
 * This custom view is designed to draw bounding boxes overlay on an image.
 * The boxes consist of a rectangle with a text representing the object's class.
 *
 * @param context The context of the view.
 * @param attrs The attribute set for the view, if applicable.
 */
class BoundingBoxOverlay(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    private var boundingBoxList = listOf<BoundingBox>()
    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()
    private var boundingBoxRect = Rect()

    init {
        initPaints()
    }

    /**
     * Clears the overlay by resetting paints and invalidating the view.
     *
     * This method resets the paints used for drawing bounding boxes and text,
     * clears the list of bounding boxes, and then invalidates the view to trigger a redraw.
     */
    fun clear() {
        textPaint.reset()
        textBackgroundPaint.reset()
        boxPaint.reset()
        invalidate()
        initPaints()
    }

    /**
     * Sets the list of bounding boxes to be drawn on the overlay and invalidates the view to trigger a redraw.
     *
     * @param boundingBoxes The list of bounding boxes to be drawn.
     */
    fun setResults(boundingBoxes: List<BoundingBox>) {
        boundingBoxList = boundingBoxes
        invalidate()
    }

    /**
     * Initializes the paints used for drawing bounding boxes and text on the overlay.
     *
     * This method sets up the paint attributes such as color, style, text size, and
     * stroke width for drawing bounding boxes and text on the overlay.
     */
    private fun initPaints() {
        textBackgroundPaint.color = Color.BLACK
        textBackgroundPaint.style = Paint.Style.FILL
        textBackgroundPaint.textSize = 50f
        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 50f
        boxPaint.color = ContextCompat.getColor(context!!, R.color.purple_500)
        boxPaint.style = Paint.Style.STROKE
        boxPaint.strokeWidth = 8F
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        boundingBoxList.forEach {
            val left = it.x1 * width
            val top = it.y1 * height
            val right = it.x2 * width
            val bottom = it.y2 * height
            canvas.drawRect(left, top, right, bottom, boxPaint)
            val drawableText = if (it.clsName != "10") it.clsName else ""
            textBackgroundPaint.getTextBounds(drawableText, 0, drawableText.length, boundingBoxRect)
            val textWidth = boundingBoxRect.width()
            val textHeight = boundingBoxRect.height()
            canvas.drawRect(
                left,
                top,
                left + textWidth + BOUNDING_BOX_RECT_TEXT_PADDING,
                top + textHeight + BOUNDING_BOX_RECT_TEXT_PADDING,
                textBackgroundPaint
            )
            canvas.drawText(drawableText, left, top + boundingBoxRect.height(), textPaint)
        }
    }

    companion object {
        private const val BOUNDING_BOX_RECT_TEXT_PADDING = 8
    }
}