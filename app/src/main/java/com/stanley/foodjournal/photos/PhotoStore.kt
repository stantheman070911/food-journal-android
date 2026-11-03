package com.stanley.foodjournal.photos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Owns every photo file, in three folders of the app's private storage:
 *
 * - `photos/`: the stored photo of each saved meal, 1600 px on the long edge
 * - `thumbs/`: a 400 px thumbnail of each, under the same name
 * - `pending/`: photos taken or picked for a meal that is not saved yet
 *
 * Every photo gets a new random name, so a replaced photo never shows a stale cached image.
 */
class PhotoStore(root: File) {
    private val photos = File(root, "photos")
    private val thumbs = File(root, "thumbs")
    private val pending = File(root, "pending")

    /** A fresh name for a new photo. */
    fun newName(): String = "${UUID.randomUUID()}.jpg"

    fun photoFile(name: String): File = File(photos, name)

    fun thumbFile(name: String): File = File(thumbs, name)

    fun pendingPhotoFile(name: String): File = File(pending, name)

    fun pendingThumbFile(name: String): File = File(pending, "thumb-$name")

    /** A scratch file in `pending/` for a camera capture before it is resized. */
    fun captureFile(name: String): File = File(pending, "capture-$name")

    fun ensureFolders() {
        listOf(photos, thumbs, pending).forEach { it.mkdirs() }
    }

    /** Moves a pending photo and its thumbnail into place, once its meal is saved. */
    suspend fun commit(name: String) = withContext(Dispatchers.IO) {
        ensureFolders()
        move(pendingPhotoFile(name), photoFile(name))
        move(pendingThumbFile(name), thumbFile(name))
    }

    /** Deletes a pending photo whose meal was discarded. */
    suspend fun discardPending(name: String) = withContext(Dispatchers.IO) {
        pendingPhotoFile(name).delete()
        pendingThumbFile(name).delete()
        captureFile(name).delete()
    }

    /** Deletes a saved photo and its thumbnail. */
    suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        photoFile(name).delete()
        thumbFile(name).delete()
    }

    /**
     * Removes saved photos and thumbnails that no meal uses, and pending files older than
     * [pendingMaxAgeMillis]. Recent pending files are left alone: a Meal screen restored after
     * Android closed the app may still need its photo.
     */
    suspend fun sweep(
        inUse: Set<String>,
        nowMillis: Long = System.currentTimeMillis(),
        pendingMaxAgeMillis: Long = TimeUnit.DAYS.toMillis(7),
    ) = withContext(Dispatchers.IO) {
        listOf(photos, thumbs).forEach { folder ->
            folder.listFiles()?.filter { it.name !in inUse }?.forEach { it.delete() }
        }
        pending.listFiles()
            ?.filter { nowMillis - it.lastModified() > pendingMaxAgeMillis }
            ?.forEach { it.delete() }
    }

    private fun move(from: File, to: File) {
        if (!from.exists()) return
        if (!from.renameTo(to)) {
            from.copyTo(to, overwrite = true)
            from.delete()
        }
    }
}
