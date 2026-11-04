package com.logos.navigator.ui.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.logos.navigator.R

internal object EnglishFloorMapLabels {
    private val patchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    fun draw(context: Context, canvas: Canvas, floor: Int, dark: Boolean = false) {
        val filter = if (dark) GeneratedFloorMaps.darkMapFilter else null
        patchPaint.colorFilter = filter
        textPaint.colorFilter = filter
        when (floor) {
            1 -> drawFirstFloor(context, canvas)
            2 -> drawSecondFloor(context, canvas)
        }
    }

    private fun drawFirstFloor(context: Context, canvas: Canvas) {
        patch(canvas, 392.5f, 119.3f, 471.8f, 131.4f, MAP_ROOM_COLOR)
        label(context, canvas, R.string.map_label_academic_office, 432.0f, 129.4f, 7.4f, ACCENT_COLOR)

        patch(canvas, 352.8f, 191.0f, 378.0f, 201.1f, MAP_ROOM_COLOR)
        patch(canvas, 385.8f, 210.2f, 448.0f, 222.1f, MAP_ROOM_COLOR)
        label(context, canvas, R.string.map_label_directors_office, 401.0f, 206.4f, 7.2f, MAP_TEXT_COLOR)

        patch(canvas, 638.0f, 155.0f, 728.0f, 182.0f, MAP_ROOM_COLOR)
        label(context, canvas, R.string.map_label_assembly_hall, 683.2f, 170.0f, 9.8f, MAP_TEXT_COLOR)

        patch(canvas, 570.0f, 248.7f, 587.0f, 256.0f, MAP_ROOM_COLOR)
        label(context, canvas, R.string.map_label_entrance, 578.5f, 254.2f, 4.5f, MAP_TEXT_COLOR)
    }

    private fun drawSecondFloor(context: Context, canvas: Canvas) {
        patch(canvas, 30.2f, 50.8f, 115.5f, 62.8f, MAP_ROOM_COLOR)
        label(context, canvas, R.string.map_label_library, 73.0f, 60.2f, 11.5f, MAP_TEXT_COLOR)

        patch(canvas, 59.2f, 141.7f, 86.0f, 154.0f, MAP_CORRIDOR_COLOR)
        label(context, canvas, R.string.map_label_hall, 72.5f, 151.7f, 11.0f, MAP_TEXT_COLOR)

        patch(canvas, 386.2f, 120.6f, 396.2f, 132.2f, MAP_ROOM_COLOR)
        label(context, canvas, R.string.map_label_lab_short, 391.2f, 130.0f, 6.8f, MAP_TEXT_COLOR)

        patch(canvas, 403.9f, 206.0f, 412.4f, 217.3f, MAP_ROOM_COLOR)
        label(context, canvas, R.string.map_label_psychologist_short, 408.5f, 214.9f, 4.6f, MAP_TEXT_COLOR)

        patch(canvas, 112.0f, 349.8f, 122.0f, 361.3f, MAP_ROOM_COLOR)
        label(context, canvas, R.string.map_label_lab_short, 117.2f, 359.0f, 6.8f, MAP_TEXT_COLOR)

        patch(canvas, 112.8f, 393.8f, 122.0f, 405.5f, MAP_ROOM_COLOR)
        label(context, canvas, R.string.map_label_practical_short, 117.5f, 402.8f, 5.5f, MAP_TEXT_COLOR)

        patch(canvas, 657.5f, 157.0f, 719.8f, 189.0f, MAP_RESTRICTED_COLOR)
        label(context, canvas, R.string.map_label_assembly_hall, 688.7f, 171.5f, 9.2f, RESTRICTED_TEXT_COLOR)
        label(context, canvas, R.string.map_label_first_floor, 688.7f, 183.8f, 9.2f, RESTRICTED_TEXT_COLOR)
    }

    private fun patch(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        patchPaint.color = color
        canvas.drawRect(left, top, right, bottom, patchPaint)
    }

    private fun label(
        context: Context,
        canvas: Canvas,
        resourceId: Int,
        centerX: Float,
        baselineY: Float,
        textSize: Float,
        color: Int,
    ) {
        val text = context.getString(resourceId)
        textPaint.color = color
        textPaint.textSize = textSize
        canvas.drawText(text, centerX, baselineY, textPaint)
    }

    private const val MAP_ROOM_COLOR = 0xFFDFE6FF.toInt()
    private const val MAP_CORRIDOR_COLOR = 0xFFFFFFFF.toInt()
    private const val MAP_RESTRICTED_COLOR = 0xFF878DA2.toInt()
    private const val MAP_TEXT_COLOR = 0xFFAFB6C9.toInt()
    private const val RESTRICTED_TEXT_COLOR = 0xFF6B7181.toInt()
    private const val ACCENT_COLOR = 0xFFE7505A.toInt()
}
