package com.stanley.foodjournal.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDateTime

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

    @Test
    fun readsAGalleryPhotosDateAndLocation() = runBlocking {
        val file = File(root, "dinner.jpg")
        writeJpeg(file, width = 800, height = 600, darkLeftEdge = false)
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:11:01 19:05:00")
            setAttribute(ExifInterface.TAG_DATETIME, "2026:11:02 08:00:00")
            setGps(25.0331, 121.5297)
            saveAttributes()
        }

        val metadata = processor.readMetadata(Uri.fromFile(file))

        assertEquals(LocalDateTime.of(2026, 11, 1, 19, 5), metadata.takenAt)
        assertEquals(25.0331, metadata.location!!.latitude, 0.0001)
        assertEquals(121.5297, metadata.location!!.longitude, 0.0001)
        assertNull(metadata.location!!.accuracyMeters)
    }

    @Test
    fun fallsBackToTheFileDateAndIgnoresAZeroedLocation() = runBlocking {
        val file = File(root, "redacted.jpg")
        writeJpeg(file, width = 800, height = 600, darkLeftEdge = false)
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_DATETIME, "2026:11:02 08:00:00")
            setGps(0.0, 0.0)
            saveAttributes()
        }

        val metadata = processor.readMetadata(Uri.fromFile(file))

        assertEquals(LocalDateTime.of(2026, 11, 2, 8, 0), metadata.takenAt)
        assertNull(metadata.location)
    }

    @Test
    fun aPhotoWithoutMetadataHasNone() = runBlocking {
        val file = File(root, "plain.jpg")
        writeJpeg(file, width = 400, height = 300, darkLeftEdge = false)

        val metadata = processor.readMetadata(Uri.fromFile(file))

        assertNull(metadata.takenAt)
        assertNull(metadata.location)
        assertNull(processor.readMetadata(Uri.fromFile(File(root, "missing.jpg"))).takenAt)
    }

    @Test
    fun reportsAnUnreadableGalleryPhotoAndLeavesNoFiles() = runBlocking {
        val file = File(root, "broken.jpg").apply { writeText("not a photo") }
        val name = store.newName()

        assertFalse(processor.fromPicker(Uri.fromFile(file), name))
        assertFalse(store.pendingPhotoFile(name).exists())
        assertFalse(store.pendingThumbFile(name).exists())
    }

    // The framework ExifInterface reads GPS but has no setter for it, so write the tags as a
    // camera does: degrees, minutes, and seconds as rationals, with a hemisphere letter.
    private fun ExifInterface.setGps(latitude: Double, longitude: Double) {
        setAttribute(ExifInterface.TAG_GPS_LATITUDE, dms(latitude))
        setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (latitude < 0) "S" else "N")
        setAttribute(ExifInterface.TAG_GPS_LONGITUDE, dms(longitude))
        setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (longitude < 0) "W" else "E")
    }

    private fun dms(value: Double): String {
        val abs = kotlin.math.abs(value)
        val degrees = abs.toInt()
        val minutes = ((abs - degrees) * 60).toInt()
        val seconds = Math.round(((abs - degrees) * 60 - minutes) * 60 * 10_000)
        return "$degrees/1,$minutes/1,$seconds/10000"
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
