package com.stanley.foodjournal.recap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stanley.foodjournal.data.Meal
import com.stanley.foodjournal.logic.Period
import com.stanley.foodjournal.logic.PeriodKind
import com.stanley.foodjournal.logic.recapMeals
import com.stanley.foodjournal.logic.recapText
import com.stanley.foodjournal.photos.PhotoStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class RecapImagesTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var photos: PhotoStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        root = File(context.cacheDir, "recap-test-${System.nanoTime()}")
        photos = PhotoStore(root).also { it.ensureFolders() }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun aWeekDrawsAsAFullSizePng() = runBlocking {
        val meals = (0 until 7).map { i ->
            // Two meals have no photo and show their name, as in the Journal.
            val photo = if (i == 2 || i == 5) null else photo(i)
            Meal(
                id = i + 1L,
                eatenAt = LocalDateTime.of(2026, 11, 2, 12, 0).plusHours(i * 9L),
                photo = photo,
                name = if (i == 5) "林家乾麵 extra spicy" else "Beef noodles",
                price = 120 + i * 30,
                placeId = (i % 3).toLong(),
            )
        }
        val week = Period(PeriodKind.Week, LocalDate.of(2026, 11, 2))
        val text = recapText(week, meals, emptyList(), LocalDate.of(2026, 11, 8))

        val file = RecapImages(context, photos).draw(recapMeals(meals), text, dark = false)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        assertEquals("image/png", bounds.outMimeType)
        assertEquals(3240 to 5760, bounds.outWidth to bounds.outHeight)
    }

    @Test
    fun aBusyYearDrawsEveryMealInTheDark() = runBlocking {
        val meals = (0 until 400).map { i ->
            Meal(id = i + 1L, eatenAt = LocalDateTime.of(2026, 1, 1, 12, 0).plusHours(i * 20L), photo = photo(i, full = false), price = 150)
        }
        val year = Period(PeriodKind.Year, LocalDate.of(2026, 1, 1))
        val text = recapText(year, meals, emptyList(), LocalDate.of(2026, 12, 31))

        val file = RecapImages(context, photos).draw(recapMeals(meals), text, dark = true)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        assertEquals(3240 to 5760, bounds.outWidth to bounds.outHeight)
    }

    /**
     * A stored thumbnail, and the photo too when [full]: a gradient, a different hue for each [i].
     * A year's tiles are small enough to be drawn from thumbnails alone.
     */
    private fun photo(i: Int, full: Boolean = true): String {
        val name = photos.newName()
        fun write(width: Int, height: Int, file: File) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val hue = (i * 47f) % 360f
            val paint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    Color.HSVToColor(floatArrayOf(hue, 0.6f, 0.9f)),
                    Color.HSVToColor(floatArrayOf((hue + 40f) % 360f, 0.8f, 0.4f)),
                    Shader.TileMode.CLAMP,
                )
            }
            Canvas(bitmap).drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            bitmap.recycle()
        }
        if (full) write(1200, 1600, photos.photoFile(name))
        write(300, 400, photos.thumbFile(name))
        return name
    }
}
