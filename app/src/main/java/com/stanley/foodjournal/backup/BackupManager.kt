package com.stanley.foodjournal.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.stanley.foodjournal.data.BackupRecords
import com.stanley.foodjournal.edits.UndoableEdits
import com.stanley.foodjournal.photos.PhotoProcessor
import com.stanley.foodjournal.photos.PhotoStore
import com.stanley.foodjournal.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** What the backup is busy with. One thing at a time. */
enum class BackupWork { Exporting, Restoring }

/** Why an export or a restore didn't happen. Nothing on the phone changed in either case. */
enum class BackupFailure {
    /** A meal's photo file is gone, so the backup would be incomplete. */
    MissingPhoto,

    /** The file couldn't be written where it was asked to go. */
    NotSaved,

    /** The checked backup couldn't replace the meals. */
    RestoreFailed,
}

data class BackupState(val work: BackupWork? = null, val failure: BackupFailure? = null)

/** A checked backup, unpacked beside the photos, waiting for the go-ahead to replace them. */
class PreparedImport internal constructor(
    internal val file: BackupFile,
    /** The meals on the phone now, which the backup would replace. */
    val currentMeals: Int,
) {
    val mealCount: Int get() = file.meals.size

    /** The day of the backup's latest meal, or null if it has none. */
    val lastMeal: LocalDate? = file.restoredMeals().maxOfOrNull { it.eatenAt }?.toLocalDate()
}

sealed interface ImportRead {
    data class Ready(val prepared: PreparedImport) : ImportRead

    data class Refused(val problem: BackupProblem) : ImportRead
}

/**
 * Exports every meal and photo to one ZIP, and restores from one. Work runs in [scope], so it
 * carries on when the screen that started it closes.
 *
 * A restore unpacks and checks the whole file before anything changes. Once confirmed, the
 * photo folders swap and one transaction replaces every meal and place; if the transaction
 * fails, the folders swap back.
 */
class BackupManager(
    private val records: BackupRecords,
    private val photos: PhotoStore,
    private val processor: PhotoProcessor,
    private val edits: UndoableEdits,
    private val settings: AppSettings,
    private val resolver: ContentResolver,
    private val scope: CoroutineScope,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) {
    private val _state = MutableStateFlow(BackupState())
    val state: StateFlow<BackupState> = _state.asStateFlow()

    private val incoming get() = File(photos.importFolder, "photos")

    /** Writes the backup to [uri], a new document from Android's save dialog. */
    fun export(uri: Uri) {
        if (!claim(BackupWork.Exporting)) return
        scope.launch {
            val failure = try {
                withContext(Dispatchers.IO) {
                    try {
                        val out = resolver.openOutputStream(uri, "wt") ?: throw FileNotFoundException("$uri")
                        writeBackup(out)
                    } catch (e: Exception) {
                        // Leave no half-written backup behind to be mistaken for a whole one.
                        runCatching { DocumentsContract.deleteDocument(resolver, uri) }
                        throw e
                    }
                }
                null
            } catch (_: MissingPhotoException) {
                BackupFailure.MissingPhoto
            } catch (e: Exception) {
                Log.w(TAG, "Export failed", e)
                BackupFailure.NotSaved
            }
            release(failure)
        }
    }

    /**
     * Writes backup.json and every meal's photo to [out] as a ZIP, closes it, and records today
     * as the last backup. Throws [MissingPhotoException] before writing anything if a meal's
     * photo file is gone.
     */
    suspend fun writeBackup(out: OutputStream) = withContext(Dispatchers.IO) {
        val snapshot = records.snapshot()
        val names = snapshot.meals.mapNotNull { it.photo }.distinct()
        if (names.any { !photos.photoFile(it).isFile }) throw MissingPhotoException()
        val exportedAt = now()
        val json = BackupJson.encodeToString(BackupFile.serializer(), backupOf(snapshot.meals, snapshot.places, exportedAt))
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(BACKUP_JSON))
            zip.write(json.encodeToByteArray())
            zip.closeEntry()
            // Stored as they are: JPEGs don't shrink further.
            for (name in names) {
                ensureActive()
                val file = photos.photoFile(name)
                zip.putNextEntry(storedEntry(BACKUP_PHOTOS + name, file))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        settings.setLastBackup(exportedAt.toLocalDate())
    }

    /** Unpacks and checks the backup at [uri]. Nothing on the phone changes. */
    suspend fun read(uri: Uri): ImportRead = read { resolver.openInputStream(uri) ?: throw FileNotFoundException("$uri") }

    /**
     * Unpacks the ZIP from [open] into the import folder, keeping only backup.json and the photos,
     * and checks it. A backup that passes waits there for [restore]; anything else is deleted.
     */
    suspend fun read(open: () -> InputStream): ImportRead = withContext(Dispatchers.IO) {
        if (_state.value.work == BackupWork.Restoring) return@withContext ImportRead.Refused(BackupProblem.Unreadable)
        photos.importFolder.deleteRecursively()
        incoming.mkdirs()
        var ready = false
        try {
            var json: String? = null
            ZipInputStream(open().buffered()).use { zip ->
                while (true) {
                    ensureActive()
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    if (entry.name == BACKUP_JSON) {
                        val bytes = zip.readNBytes(MAX_JSON_BYTES + 1)
                        if (bytes.size > MAX_JSON_BYTES) return@withContext ImportRead.Refused(BackupProblem.Damaged("backup.json is too large"))
                        json = bytes.decodeToString()
                    } else {
                        val name = photoEntryName(entry.name) ?: continue
                        File(incoming, name).outputStream().use { zip.copyTo(it) }
                    }
                }
            }
            when (val check = readBackup(json, incoming.list().orEmpty().toSet())) {
                is BackupCheck.Failed -> ImportRead.Refused(check.problem)
                is BackupCheck.Passed -> {
                    ready = true
                    ImportRead.Ready(PreparedImport(check.file, records.mealCount()))
                }
            }
        } catch (e: ZipException) {
            Log.w(TAG, "Import isn't a readable ZIP", e)
            ImportRead.Refused(BackupProblem.Damaged("not a readable ZIP"))
        } catch (e: IOException) {
            Log.w(TAG, "Import couldn't be read", e)
            ImportRead.Refused(BackupProblem.Unreadable)
        } finally {
            if (!ready) withContext(NonCancellable) { photos.importFolder.deleteRecursively() }
        }
    }

    /** Deletes a read backup that won't be restored. */
    suspend fun discard() = withContext(Dispatchers.IO) {
        if (_state.value.work != BackupWork.Restoring) photos.importFolder.deleteRecursively()
    }

    /**
     * Replaces every meal, place, and photo with those of [prepared], the backup last read. An
     * open undo bar closes first. True once done; false if it failed, leaving everything as it
     * was. Runs in the app's scope, so it finishes even if the caller goes away.
     */
    suspend fun restore(prepared: PreparedImport): Boolean {
        if (!claim(BackupWork.Restoring)) return false
        return scope.async {
            val meals = prepared.file.restoredMeals()
            val done = try {
                edits.withBarClosed {
                    photos.swapIn(incoming)
                    try {
                        records.replaceAll(prepared.file.restoredPlaces(), meals)
                    } catch (e: Exception) {
                        photos.swapBack()
                        throw e
                    }
                }
                true
            } catch (e: Exception) {
                Log.w(TAG, "Restore failed", e)
                false
            }
            try {
                if (done) {
                    photos.dropOldPhotos()
                    val inUse = meals.mapNotNull { it.photo }.toSet()
                    // Thumbnails of photos the phone already had stay; the rest are rebuilt.
                    photos.sweep(inUse)
                    scope.launch { rebuildThumbs(inUse) }
                    prepared.file.exportDate()?.let { settings.setLastBackup(it) }
                }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { photos.importFolder.deleteRecursively() }
                release(if (done) null else BackupFailure.RestoreFailed)
            }
            done
        }.await()
    }

    /** Forgets the last failure, once it has been shown. */
    fun clearFailure() {
        _state.update { it.copy(failure = null) }
    }

    private suspend fun rebuildThumbs(names: Set<String>) {
        for (name in names) {
            if (photos.thumbFile(name).exists()) continue
            if (!processor.rebuildThumb(name)) Log.w(TAG, "No thumbnail for $name")
        }
    }

    /** Starts [work] unless something is already running. */
    private fun claim(work: BackupWork): Boolean {
        while (true) {
            val current = _state.value
            if (current.work != null) return false
            if (_state.compareAndSet(current, BackupState(work = work))) return true
        }
    }

    private fun release(failure: BackupFailure?) {
        _state.value = BackupState(failure = failure)
    }
}

class MissingPhotoException : IOException("A meal's photo is missing")

/** A ZIP entry stored without compression, which needs its size and checksum up front. */
private fun storedEntry(name: String, file: File): ZipEntry {
    val crc = CRC32()
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            crc.update(buffer, 0, read)
        }
    }
    return ZipEntry(name).apply {
        method = ZipEntry.STORED
        size = file.length()
        compressedSize = file.length()
        this.crc = crc.value
    }
}

/** Far more than tens of thousands of meals need; a larger file isn't one of ours. */
private const val MAX_JSON_BYTES = 64 * 1024 * 1024

private const val TAG = "FoodJournal"
