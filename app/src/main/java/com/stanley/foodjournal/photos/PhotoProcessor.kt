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
import android.util.Log
import com.stanley.foodjournal.logic.JPEG_QUALITY
import com.stanley.foodjournal.logic.LocationFix
import com.stanley.foodjournal.logic.PHOTO_LONG_EDGE
import com.stanley.foodjournal.logic.PixelSize
import com.stanley.foodjournal.logic.THUMB_LONG_EDGE
import com.stanley.foodjournal.logic.fitWithin
import com.stanley.foodjournal.logic.parseExifDateTime
import com.stanley.foodjournal.logic.photoLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDateTime

/** What a gallery photo's metadata says about when and where it was taken. */
data class PhotoMetadata(
    val takenAt: LocalDateTime?,
    val location: LocationFix?,
)

/** Turns new photos into pending photos. An interface so tests can decide when it finishes. */
interface PhotoProcessing {
    /** Processes the capture in [PhotoStore.captureFile] and deletes it. False if unreadable. */
    suspend fun fromCapture(name: String): Boolean

    /** A gallery photo's date and location, read quickly, before the photo is processed. */
    suspend fun readMetadata(uri: Uri): PhotoMetadata

    /** Processes a photo from the Photo Picker. False if it can't be read. */
    suspend fun fromPicker(uri: Uri, name: String): Boolean
}

/**
 * Turns a camera capture or a gallery photo into a pending photo: 1600 px on the long edge and
 * a 400 px thumbnail, both JPEG at quality 85. The stored files carry no metadata.
 */
class PhotoProcessor(
    private val store: PhotoStore,
    private val resolver: ContentResolver,
) : PhotoProcessing {
    /** Lets one thumbnail rebuild run at a time, so a launch's and a restore's never write one file together. */
    private val thumbRebuild = Mutex()

    // Any failure, not only I/O: a photo the decoder rejects, a revoked grant, or too little
    // memory all leave the meal without that photo rather than crashing the app.
    override suspend fun fromCapture(name: String): Boolean = withContext(Dispatchers.IO) {
        val capture = store.captureFile(name)
        try {
            write(ImageDecoder.createSource(capture), name)
            true
        } catch (_: Exception) {
            discardPartial(name)
            false
        } catch (_: OutOfMemoryError) {
            discardPartial(name)
            false
        } finally {
            capture.delete()
        }
    }

    /** The location is only there when the picker was allowed to share it. */
    override suspend fun readMetadata(uri: Uri): PhotoMetadata = withContext(Dispatchers.IO) {
        try {
            exifMetadata(uri)
        } catch (_: Exception) {
            PhotoMetadata(takenAt = null, location = null)
        }
    }

    override suspend fun fromPicker(uri: Uri, name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            write(ImageDecoder.createSource(resolver, uri), name)
            true
        } catch (_: Exception) {
            discardPartial(name)
            false
        } catch (_: OutOfMemoryError) {
            discardPartial(name)
            false
        }
    }

    /**
     * Writes the 400 px thumbnail of the stored photo [name] again, as after a restore, which
     * brings back photos without their thumbnails. False if the photo can't be read.
     */
    suspend fun rebuildThumb(name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val thumb = ImageDecoder.decodeBitmap(ImageDecoder.createSource(store.photoFile(name))) { decoder, info, _ ->
                val size = fitWithin(PixelSize(info.size.width, info.size.height), THUMB_LONG_EDGE)
                decoder.setTargetSize(size.width, size.height)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            store.ensureFolders()
            writeJpeg(thumb, store.thumbFile(name))
            thumb.recycle()
            true
        } catch (_: Exception) {
            File(store.thumbFile(name).parentFile, "$name.part").delete()
            false
        } catch (_: OutOfMemoryError) {
            false
        }
    }

    /**
     * Writes the thumbnail of each stored photo in [names] that has none, as after a restore, or
     * a run that ended before all its thumbnails were written. A photo that can't be read is
     * logged and skipped.
     */
    suspend fun rebuildMissingThumbs(names: Collection<String>) {
        thumbRebuild.withLock {
            for (name in names) {
                if (store.thumbFile(name).exists()) continue
                if (!rebuildThumb(name)) Log.w(TAG, "No thumbnail for $name")
            }
        }
    }

    private fun discardPartial(name: String) {
        listOf(store.pendingPhotoFile(name), store.pendingThumbFile(name)).forEach { file ->
            file.delete()
            File(file.parentFile, "${file.name}.part").delete()
        }
    }

    private fun exifMetadata(uri: Uri): PhotoMetadata {
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

private const val TAG = "FoodJournal"
