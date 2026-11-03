// Lint prefers core-ktx's createBitmap, withClip, and withTranslation, which aren't in the stack.
@file:Suppress("UseKtx")

package com.stanley.foodjournal.recap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import com.stanley.foodjournal.R
import com.stanley.foodjournal.data.Meal
import com.stanley.foodjournal.logic.RecapText
import com.stanley.foodjournal.logic.THUMB_LONG_EDGE
import com.stanley.foodjournal.logic.TileRect
import com.stanley.foodjournal.logic.collageTiles
import com.stanley.foodjournal.photos.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Draws a period's recap image. An interface so tests can stand in for the drawing. */
fun interface RecapDrawing {
    /** Draws [meals], already chosen for the collage, above [text], and returns the image file. */
    suspend fun draw(meals: List<Meal>, text: RecapText, dark: Boolean): File
}

/**
 * Draws a recap at three times Instagram Stories size, 3240 by 5760: every meal's photo as a
 * collage, with the period and its figures on a plain band below, white or black as the app's
 * theme is. A meal without a photo is the Journal's plain tile with its name. Layout is worked
 * out at Stories size and drawn scaled up, so text stays sharp and each photo is decoded at the
 * pixels its tile covers. The image is a lossless PNG in the cache, where Android's share sheet
 * reads it; each new recap replaces the last.
 */
class RecapImages(private val context: Context, private val photos: PhotoStore) : RecapDrawing {
    private val folder = File(context.cacheDir, RECAP_FOLDER)

    override suspend fun draw(meals: List<Meal>, text: RecapText, dark: Boolean): File = withContext(Dispatchers.IO) {
        val colors = if (dark) DarkInk else LightInk
        val image = Bitmap.createBitmap(WIDTH * SCALE, HEIGHT * SCALE, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(image)
            canvas.drawColor(colors.background)
            canvas.scale(SCALE.toFloat(), SCALE.toFloat())

            val collage = RectF(MARGIN, MARGIN, WIDTH - MARGIN, HEIGHT - BAND)
            canvas.save()
            // Only the collage's outer corners are rounded, so it reads as one picture.
            canvas.clipPath(Path().apply { addRoundRect(collage, CORNER, CORNER, Path.Direction.CW) })
            canvas.translate(collage.left, collage.top)
            // Gaps narrow as tiles multiply, so a year's hundreds of meals stay mostly photo.
            val gap = (GAP / sqrt(meals.size / 12.0).toFloat()).coerceIn(MIN_GAP, GAP)
            val tiles = collageTiles(meals.size, collage.width(), collage.height(), gap)
            meals.zip(tiles).forEach { (meal, tile) ->
                ensureActive()
                drawTile(canvas, meal, tile, colors)
            }
            canvas.restore()

            drawBand(canvas, text, colors, top = HEIGHT - BAND)

            folder.deleteRecursively()
            folder.mkdirs()
            val file = File(folder, RECAP_FILE)
            file.outputStream().buffered().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            file
        } finally {
            image.recycle()
        }
    }

    private fun drawTile(canvas: Canvas, meal: Meal, tile: TileRect, colors: Ink) {
        val bounds = RectF(tile.left, tile.top, tile.right, tile.bottom)
        val photo = meal.photo?.let { name ->
            // A tile no bigger than the thumbnail reads it, which is far quicker for a year's meals.
            val files = listOf(photos.photoFile(name), photos.thumbFile(name))
            val small = max(bounds.width(), bounds.height()) * SCALE <= THUMB_LONG_EDGE
            (if (small) files.reversed() else files).firstNotNullOfOrNull { decodeCovering(it, bounds) }
        }
        if (photo != null) {
            // Cropped from the middle to the tile's shape, as the Journal's tiles are.
            val scale = max(bounds.width() / photo.width, bounds.height() / photo.height)
            val cropWidth = bounds.width() / scale
            val cropHeight = bounds.height() / scale
            val left = (photo.width - cropWidth) / 2
            val top = (photo.height - cropHeight) / 2
            val source = Rect(left.roundToInt(), top.roundToInt(), (left + cropWidth).roundToInt(), (top + cropHeight).roundToInt())
            canvas.drawBitmap(photo, source, bounds, Paint(Paint.FILTER_BITMAP_FLAG))
            photo.recycle()
        } else {
            drawPlainTile(canvas, meal.name.orEmpty(), bounds, colors)
        }
    }

    /** The Journal's tile for a meal without a photo: plain grey, with its name at the top. */
    private fun drawPlainTile(canvas: Canvas, name: String, bounds: RectF, colors: Ink) {
        canvas.drawRect(bounds, Paint().apply { color = colors.tile })
        val padding = bounds.width() * TILE_PADDING
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.ink
            typeface = Typeface.create(Typeface.DEFAULT, 600, false)
            textSize = (bounds.width() * TILE_NAME_SIZE).coerceIn(3f, 64f)
        }
        val width = (bounds.width() - padding * 2).roundToInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(name, 0, name.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
            .setMaxLines(4)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(bounds.left + padding, bounds.top + padding)
        layout.draw(canvas)
        canvas.restore()
    }

    /** The period in the serif, large, with the year at the right, and the figures below. */
    private fun drawBand(canvas: Canvas, text: RecapText, colors: Ink, top: Float) {
        val sans = Typeface.DEFAULT
        val sideTop = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.ink
            typeface = Typeface.create(sans, 500, false)
            textSize = SIDE_SIZE
            textAlign = Paint.Align.RIGHT
        }
        val sideBottom = TextPaint(sideTop).apply { color = colors.grey }
        val sideWidth = max(text.year?.let(sideTop::measureText) ?: 0f, sideBottom.measureText(BRAND))

        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.ink
            typeface = context.resources.getFont(R.font.fraunces)
            fontVariationSettings = "'wght' 300, 'opsz' 144, 'SOFT' 100, 'WONK' 0"
            letterSpacing = -0.02f
            textSize = TITLE_SIZE
        }
        // A long week, such as "Dec 29 – Jan 4", shrinks until it clears the year.
        val room = WIDTH - MARGIN * 2 - sideWidth - SIDE_GAP
        while (title.measureText(text.title) > room && title.textSize > MIN_TITLE_SIZE) title.textSize -= 4f
        val capHeight = Rect().also { title.getTextBounds("N", 0, 1, it) }.height()
        val titleBaseline = top + BAND_PADDING + capHeight
        canvas.drawText(text.title, MARGIN, titleBaseline, title)

        val sideX = WIDTH - MARGIN
        val firstSide = top + BAND_PADDING + Rect().also { sideTop.getTextBounds("2", 0, 1, it) }.height()
        val year = text.year
        if (year != null) {
            canvas.drawText(year, sideX, firstSide, sideTop)
            canvas.drawText(BRAND, sideX, firstSide + SIDE_SIZE * 1.3f, sideBottom)
        } else {
            canvas.drawText(BRAND, sideX, firstSide, sideBottom)
        }

        val stats = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.ink
            typeface = Typeface.create(sans, 500, false)
            textSize = STATS_SIZE
            fontFeatureSettings = "tnum"
        }
        val statsBaseline = titleBaseline + STATS_GAP
        canvas.drawText(text.stats, MARGIN, statsBaseline, stats)
        text.comparison?.let { comparison ->
            val paint = TextPaint(stats).apply {
                color = colors.grey
                typeface = sans
                textSize = COMPARISON_SIZE
            }
            canvas.drawText(comparison, MARGIN, statsBaseline + COMPARISON_GAP, paint)
        }
    }

    /**
     * [file] decoded no larger than needed to cover [bounds], or null if it can't be read, as a
     * thumbnail not yet rebuilt after a restore.
     */
    private fun decodeCovering(file: File, bounds: RectF): Bitmap? {
        if (!file.isFile) return null
        return try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val scale = (max(bounds.width() / info.size.width, bounds.height() / info.size.height) * SCALE).coerceAtMost(1f)
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (e: Exception) {
            Log.w(TAG, "Recap couldn't read ${file.name}", e)
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private class Ink(val background: Int, val ink: Int, val grey: Int, val tile: Int)

    private companion object {
        // The app's own black, white, and greys, as in ui/theme/Color.kt.
        val LightInk = Ink(background = 0xFFFFFFFF.toInt(), ink = 0xFF000000.toInt(), grey = 0xFF6E6E6E.toInt(), tile = 0xFFF2F2F2.toInt())
        val DarkInk = Ink(background = 0xFF000000.toInt(), ink = 0xFFF2F2F2.toInt(), grey = 0xFF9A9A9A.toInt(), tile = 0xFF1C1C1C.toInt())

        // Layout units are Stories pixels; the image is drawn SCALE times larger.
        const val WIDTH = 1080
        const val HEIGHT = 1920
        const val SCALE = 3
        const val MARGIN = 40f
        const val BAND = 440f
        const val GAP = 6f
        const val MIN_GAP = 0.5f
        const val CORNER = 36f
        const val TILE_PADDING = 0.08f
        const val TILE_NAME_SIZE = 0.12f
        const val BAND_PADDING = 72f
        const val TITLE_SIZE = 150f
        const val MIN_TITLE_SIZE = 80f
        const val SIDE_SIZE = 42f
        const val SIDE_GAP = 32f
        const val STATS_SIZE = 42f
        const val STATS_GAP = 100f
        const val COMPARISON_SIZE = 38f
        const val COMPARISON_GAP = 58f
        const val BRAND = "Food Journal"
        const val TAG = "FoodJournal"
    }
}

/** The cache folder recaps are written to, which the manifest's file provider shares. */
const val RECAP_FOLDER = "recaps"

private const val RECAP_FILE = "food-journal-recap.png"
