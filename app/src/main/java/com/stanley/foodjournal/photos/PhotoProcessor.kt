// Lint prefers the AndroidX ExifInterface for fixes to old Android versions, and core-ktx's
// Bitmap.scale. With minSdk 37 the framework ExifInterface has the fixes, and neither library
// is in the stack.
@file:Suppress("ExifInterface", "UseKtx")

package com.stanley.foodjournal.photos

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import com.stanley.foodjournal.logic.JPEG_QUALITY
import com.stanley.foodjournal.logic.LocationFix
import com.stanley.foodjournal.logic.PHOTO_LONG_EDGE
import com.stanley.foodjournal.logic.PixelSize
import com.stanley.foodjournal.logic.THUMB_LONG_EDGE
import com.stanley.foodjournal.logic.fitWithin
import com.stanley.foodjournal.logic.parseExifDateTime
import com.stanley.foodjournal.logic.photoLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDateTime

/** What a gallery photo's metadata says about when and where it was taken. */
data class PhotoMetadata(
    val takenAt: LocalDateTime?,
    val location: LocationFix?,
)

/**
 * Turns a camera capture or a gallery photo into a pending photo: 1600 px on the long edge and
 * a 400 px thumbnail, both JPEG at quality 85. The stored files carry no metadata.
 */
class PhotoProcessor(
    private val store: PhotoStore,
    private val resolver: ContentResolver,
) {
    /** Processes the capture in [PhotoStore.captureFile] and deletes it. False if unreadable. */
    suspend fun fromCapture(name: String): Boolean = withContext(Dispatchers.IO) {
        val capture = store.captureFile(name)
        try {
            write(ImageDecoder.createSource(capture), name)
            true
        } catch (_: IOException) {
            false
        } finally {
            capture.delete()
        }
    }

    /**
     * Processes a photo from the Photo Picker and reads its metadata. The location is only
     * there when the picker was allowed to share it. Null if the photo can't be read.
     */
    suspend fun fromPicker(uri: Uri, name: String): PhotoMetadata? = withContext(Dispatchers.IO) {
        try {
            val metadata = readMetadata(uri)
            write(ImageDecoder.createSource(resolver, uri), name)
            metadata
        } catch (_: IOException) {
            null
        }
    }

    private fun readMetadata(uri: Uri): PhotoMetadata {
        val exif = resolver.openInputStream(uri)?.use { ExifInterface(it) }
            ?: return PhotoMetadata(takenAt = null, location = null)
        val takenAt = parseExifDateTime(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
            ?: parseExifDateTime(exif.getAttribute(ExifInterface.TAG_DATETIME))
        val latLong = FloatArray(2)
        val location = if (exif.getLatLong(latLong)) photoLocation(latLong[0].toDouble(), latLong[1].toDouble()) else null
        return PhotoMetadata(takenAt, location)
    }

    // ImageDecoder applies the EXIF rotation itself, so the stored photo is upright.
    private fun write(source: ImageDecoder.Source, name: String) {
        val photo = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val size = fitWithin(PixelSize(info.size.width, info.size.height), PHOTO_LONG_EDGE)
            decoder.setTargetSize(size.width, size.height)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        store.ensureFolders()
        writeJpeg(photo, store.pendingPhotoFile(name))
        val thumbSize = fitWithin(PixelSize(photo.width, photo.height), THUMB_LONG_EDGE)
        val thumb = Bitmap.createScaledBitmap(photo, thumbSize.width, thumbSize.height, true)
        writeJpeg(thumb, store.pendingThumbFile(name))
        thumb.recycle()
        photo.recycle()
    }

    private fun writeJpeg(bitmap: Bitmap, file: File) {
        val partial = File(file.parentFile, "${file.name}.part")
        partial.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        if (!partial.renameTo(file)) throw IOException("Could not write ${file.name}")
    }
}
