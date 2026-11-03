package com.stanley.foodjournal.backup

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stanley.foodjournal.data.BackupRecords
import com.stanley.foodjournal.data.FoodJournalDatabase
import com.stanley.foodjournal.edits.UndoableEdits
import com.stanley.foodjournal.photos.PhotoProcessor
import com.stanley.foodjournal.photos.PhotoStore
import com.stanley.foodjournal.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.milliseconds

/**
 * Everything a backup needs, on an in-memory database and a scratch folder: the app's own
 * classes, so a test runs the real export and restore. Close it after each test.
 */
class BackupFixture(
    val now: LocalDateTime = LocalDateTime.of(2026, 11, 4, 1, 15),
    records: ((FoodJournalDatabase) -> BackupRecords)? = null,
) {
    val context: Context = ApplicationProvider.getApplicationContext()
    val root = File(context.cacheDir, "backup-test-${System.nanoTime()}").apply { mkdirs() }
    val db: FoodJournalDatabase = Room.inMemoryDatabaseBuilder(context, FoodJournalDatabase::class.java).build()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val photos = PhotoStore(File(root, "files")).also { it.ensureFolders() }
    val settings = AppSettings(PreferenceDataStoreFactory.create(scope = scope) { File(root, "settings.preferences_pb") })
    val edits = UndoableEdits(db.mealDao(), db.placeDao(), photos, scope, window = 400.milliseconds)
    val backup = BackupManager(
        records = records?.invoke(db) ?: db.backupDao(),
        photos = photos,
        processor = PhotoProcessor(photos, context.contentResolver),
        edits = edits,
        settings = settings,
        resolver = context.contentResolver,
        scope = scope,
        now = { now },
    )

    /** A small real JPEG, saved as a meal's photo under [name], so thumbnails can be rebuilt. */
    fun savePhoto(name: String, color: Int = Color.DKGRAY): String {
        val bitmap = Bitmap.createBitmap(120, 160, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        photos.photoFile(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bitmap.recycle()
        return name
    }

    /** A file in the scratch folder, for a backup to be written to or read from. */
    fun file(name: String) = File(root, name)

    fun close() {
        // Wait for work still writing to the database, or closing it under that work crashes
        // the test process.
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        db.close()
        root.deleteRecursively()
    }
}

suspend fun awaitTrue(condition: suspend () -> Boolean) {
    withTimeout(5_000) {
        while (!condition()) delay(20)
    }
}
