package app.auralis

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.ColorUtils
import coil.size.Size
import coil.transform.Transformation

/** Small, cached cover treatments; extension data selects a bounded native renderer, never executable code. */
internal class ArtworkTreatment(
    private val style: String,
    private val primary: Int,
    private val background: Int,
) : Transformation {
    override val cacheKey = "reva-artwork-v1:$style:$primary:$background"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap = when (style) {
        "ascii" -> ascii(input)
        "monochrome" -> mapPixels(input) { color ->
            val gray = (android.graphics.Color.red(color) * .2126 + android.graphics.Color.green(color) * .7152 + android.graphics.Color.blue(color) * .0722).toInt()
            android.graphics.Color.argb(android.graphics.Color.alpha(color), gray, gray, gray)
        }
        "duotone" -> mapPixels(input) { color ->
            val luminance = ColorUtils.calculateLuminance(color).toFloat()
            ColorUtils.blendARGB(background, primary, luminance)
        }
        else -> input
    }

    private fun mapPixels(input: Bitmap, map: (Int) -> Int): Bitmap {
        val pixels = IntArray(input.width * input.height)
        input.getPixels(pixels, 0, input.width, 0, 0, input.width, input.height)
        for (i in pixels.indices) pixels[i] = map(pixels[i])
        return Bitmap.createBitmap(pixels, 0, input.width, input.width, input.height, Bitmap.Config.ARGB_8888)
    }

    private fun ascii(input: Bitmap): Bitmap {
        val side = 128
        val grid = 24
        val cell = side.toFloat() / grid
        val scaled = Bitmap.createScaledBitmap(input, grid, grid, true)
        val pixels = IntArray(grid * grid)
        scaled.getPixels(pixels, 0, grid, 0, 0, grid, grid)
        if (scaled !== input) scaled.recycle()

        val output = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(background)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
            textSize = cell * 1.02f
        }
        val ramp = " .:-=+*#%@"
        for (y in 0 until grid) for (x in 0 until grid) {
            val color = pixels[y * grid + x]
            val light = ColorUtils.calculateLuminance(color)
            val glyph = ramp[(light * (ramp.lastIndex)).toInt().coerceIn(ramp.indices)]
            if (glyph != ' ') {
                paint.color = ColorUtils.blendARGB(primary, color, .55f)
                canvas.drawText(glyph.toString(), (x + .5f) * cell, (y + .5f) * cell + cell * .34f, paint)
            }
        }
        return output
    }
}
