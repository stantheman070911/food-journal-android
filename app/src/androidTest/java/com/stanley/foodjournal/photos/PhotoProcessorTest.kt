package com.stanley.foodjournal.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PhotoProcessorTest {
    private lateinit var root: File
    private lateinit var store: PhotoStore
    private lateinit var processor: PhotoProcessor

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        root = File(context.cacheDir, "processor-test-${System.nanoTime()}")
        store = PhotoStore(root).also { it.ensureFolders() }
        processor = PhotoProcessor(store, context.contentResolver)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun rotatesAndShrinksALargeSidewaysCapture() = runBlocking {
        val name = store.newName()
        // 3000 × 2000 landscape pixels, dark along the left edge, tagged "rotate 90° clockwise".
        writeJpeg(store.captureFile(name), width = 3000, height = 2000, darkLeftEdge = true)
        ExifInterface(store.captureFile(name)).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }

        assertTrue(processor.fromCapture(name))

        val photo = BitmapFactory.decodeFile(store.pendingPhotoFile(name).path)
        assertEquals(1067, photo.width)
        assertEquals(1600, photo.height)
        // The dark left edge is now the top: the photo is upright.
        assertTrue(Color.luminance(photo.getPixel(photo.width / 2, 10)) < 0.2f)
        assertTrue(Color.luminance(photo.getPixel(photo.width / 2, photo.height - 10)) > 0.5f)

        val thumb = BitmapFactory.decodeFile(store.pendingThumbFile(name).path)
        assertEquals(267, thumb.width)
        assertEquals(400, thumb.height)
        assertFalse(store.captureFile(name).exists())
    }

    @Test
    fun keepsASmallPhotoAtItsSize() = runBlocking {
        val name = store.newName()
        writeJpeg(store.captureFile(name), width = 1280, height = 960, darkLeftEdge = false)

        assertTrue(processor.fromCapture(name))

        val photo = BitmapFactory.decodeFile(store.pendingPhotoFile(name).path)
        assertEquals(1280, photo.width)
        assertEquals(960, photo.height)
    }

    @Test
    fun reportsAnUnreadableCapture() = runBlocking {
        val name = store.newName()
        store.captureFile(name).writeText("not a photo")

        assertFalse(processor.fromCapture(name))
        assertFalse(store.pendingPhotoFile(name).exists())
    }

    private fun writeJpeg(file: File, width: Int, height: Int, darkLeftEdge: Boolean) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(230, 200, 160))
            if (darkLeftEdge) drawRect(0f, 0f, width * 0.15f, height.toFloat(), Paint().apply { color = Color.rgb(30, 30, 30) })
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }
}
