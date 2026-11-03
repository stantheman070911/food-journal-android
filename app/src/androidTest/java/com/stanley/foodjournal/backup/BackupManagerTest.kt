package com.stanley.foodjournal.backup

import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stanley.foodjournal.data.BackupRecords
import com.stanley.foodjournal.data.FoodJournalDatabase
import com.stanley.foodjournal.data.Meal
import com.stanley.foodjournal.data.Place
import com.stanley.foodjournal.data.Records
import com.stanley.foodjournal.edits.PendingEdit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class BackupManagerTest {
    private val fixtures = mutableListOf<BackupFixture>()
    private val f = fixture()
    private val noon = LocalDateTime.of(2026, 10, 30, 12, 30)

    @After
    fun tearDown() = fixtures.forEach { it.close() }

    @Test
    fun anExportRestoresExactlyWhatItHeld() = runBlocking {
        val saved = seed(f)
        val zip = f.file("backup.zip")
        f.backup.writeBackup(zip.outputStream())
        assertEquals(LocalDate.of(2026, 11, 4), f.settings.lastBackup.first())

        // Then the phone moves on: a meal deleted, another added, a place renamed.
        f.db.mealDao().delete(saved.meals[0])
        f.db.mealDao().insert(Meal(eatenAt = noon.plusDays(3), photo = f.savePhoto("c.jpg"), price = 90))
        f.db.placeDao().update(saved.places[0].copy(name = "Renamed"))
        f.settings.setLastBackup(LocalDate.of(2026, 11, 20))

        val prepared = (f.backup.read { zip.inputStream() } as ImportRead.Ready).prepared
        assertEquals(3, prepared.mealCount)
        assertEquals(LocalDate.of(2026, 10, 31), prepared.lastMeal)
        assertEquals(3, prepared.currentMeals)

        assertTrue(f.backup.restore(prepared))

        assertEquals(saved, f.db.backupDao().snapshot())
        assertTrue(f.photos.photoFile("a.jpg").exists())
        assertTrue(f.photos.photoFile("b.jpg").exists())
        assertFalse(f.photos.photoFile("c.jpg").exists())
        assertFalse(f.photos.importFolder.exists())
        assertFalse(File(f.photos.importFolder.parentFile, "photos-old").exists())
        // The backup's own date, since the phone now holds exactly what it holds.
        assertEquals(LocalDate.of(2026, 11, 4), f.settings.lastBackup.first())
        awaitTrue { f.photos.thumbFile("a.jpg").exists() && f.photos.thumbFile("b.jpg").exists() }
        assertEquals(BackupState(), f.backup.state.value)
    }

    @Test
    fun theZipHoldsTheRecordsAndTheStoredPhotosOnly() = runBlocking {
        seed(f)
        f.photos.thumbFile("a.jpg").writeText("thumb")
        val zip = f.file("backup.zip")

        f.backup.writeBackup(zip.outputStream())

        ZipFile(zip).use { file ->
            val entries = file.entries().toList().associateBy { it.name }
            assertEquals(setOf(BACKUP_JSON, "photos/a.jpg", "photos/b.jpg"), entries.keys)
            assertEquals(ZipEntry.DEFLATED, entries.getValue(BACKUP_JSON).method)
            assertEquals(ZipEntry.STORED, entries.getValue("photos/a.jpg").method)
            assertEquals(f.photos.photoFile("a.jpg").length(), entries.getValue("photos/a.jpg").size)
            val json = file.getInputStream(entries.getValue(BACKUP_JSON)).bufferedReader().readText()
            assertTrue(json.contains("\"exportedAt\": \"2026-11-04T01:15:00\""))
        }
    }

    @Test
    fun anExportThroughAUriWritesTheFileAndNotesTheDay() = runBlocking {
        seed(f)
        val zip = f.file("through-uri.zip").apply { writeText("an older file in the way") }

        f.backup.export(Uri.fromFile(zip))
        awaitTrue { f.backup.state.value.work == null }

        assertEquals(BackupState(), f.backup.state.value)
        assertEquals(LocalDate.of(2026, 11, 4), f.settings.lastBackup.first())
        ZipFile(zip).use { assertEquals(3, it.size()) }
    }

    @Test
    fun anExportWithAMissingPhotoIsRefusedBeforeWritingAnything() = runBlocking {
        seed(f)
        f.photos.photoFile("b.jpg").delete()

        try {
            f.backup.writeBackup(ByteArrayOutputStream())
            fail("A backup without b.jpg was written")
        } catch (_: MissingPhotoException) {
        }
        f.backup.export(Uri.fromFile(f.file("missing.zip")))
        awaitTrue { f.backup.state.value.work == null }

        assertEquals(BackupFailure.MissingPhoto, f.backup.state.value.failure)
        assertNull(f.settings.lastBackup.first())
        f.backup.clearFailure()
        assertEquals(BackupState(), f.backup.state.value)
    }

    @Test
    fun refusedFilesChangeNothing() = runBlocking {
        val saved = seed(f)
        val sound = soundBackup()
        val cases = listOf(
            "not a ZIP".toByteArray() to BackupProblem.NotABackup,
            zipOf("photos/a.jpg" to "photo") to BackupProblem.NotABackup,
            zipOf(BACKUP_JSON to """{"format":2}""") to BackupProblem.NewerVersion,
            // The records name a.jpg and b.jpg, but only a.jpg came along.
            withoutEntry(sound, "photos/b.jpg") to BackupProblem.Damaged("meal 2's photo is missing"),
        )

        for ((bytes, problem) in cases) {
            assertEquals(ImportRead.Refused(problem), f.backup.read { ByteArrayInputStream(bytes) })
            assertFalse(f.photos.importFolder.exists())
        }
        assertEquals(saved, f.db.backupDao().snapshot())
        assertTrue(f.photos.photoFile("a.jpg").exists())
        assertTrue(f.photos.photoFile("b.jpg").exists())
    }

    @Test
    fun aFileCutShortChangesNothing() = runBlocking {
        val saved = seed(f)
        val bytes = soundBackup()

        val read = f.backup.read { ByteArrayInputStream(bytes.copyOf(bytes.size * 2 / 3)) }

        assertTrue(read is ImportRead.Refused)
        assertFalse(f.photos.importFolder.exists())
        assertEquals(saved, f.db.backupDao().snapshot())
    }

    @Test
    fun entriesBesideTheRecordsAndPhotosAreIgnored() = runBlocking {
        val bytes = withEntries(soundBackup(), "notes.txt" to "x", "photos/sub/c.jpg" to "x", "thumbs/a.jpg" to "x")

        val read = f.backup.read { ByteArrayInputStream(bytes) }

        assertTrue(read is ImportRead.Ready)
        assertEquals(setOf("a.jpg", "b.jpg"), File(f.photos.importFolder, "photos").list()!!.toSet())
        assertEquals(setOf("photos"), f.photos.importFolder.list()!!.toSet())
    }

    @Test
    fun aPathThatEscapesTheFolderGetsTheFileRefused() = runBlocking {
        for (escape in listOf("photos/../escaped.jpg", "../escaped.jpg")) {
            val bytes = withEntries(soundBackup(), escape to "x")

            val read = f.backup.read { ByteArrayInputStream(bytes) }

            // Android's own ZIP reader refuses such a name, and with it the whole file.
            assertTrue(read is ImportRead.Refused)
            assertFalse(File(f.photos.importFolder.parentFile, "escaped.jpg").exists())
            assertFalse(File(f.root, "escaped.jpg").exists())
            assertFalse(f.photos.importFolder.exists())
        }
    }

    @Test
    fun aFailedReplacementPutsThePhotosBack() = runBlocking {
        val failing = fixture { db ->
            val real = db.backupDao()
            object : BackupRecords by real {
                override suspend fun replaceAll(places: List<Place>, meals: List<Meal>) = throw IOException("disk full")
            }
        }
        val saved = seed(failing)
        val bytes = soundBackup()
        val prepared = (failing.backup.read { ByteArrayInputStream(bytes) } as ImportRead.Ready).prepared
        failing.photos.photoFile("a.jpg").writeText("the phone's own a.jpg")

        assertFalse(failing.backup.restore(prepared))

        assertEquals(BackupFailure.RestoreFailed, failing.backup.state.value.failure)
        assertEquals(saved, failing.db.backupDao().snapshot())
        assertEquals("the phone's own a.jpg", failing.photos.photoFile("a.jpg").readText())
        assertTrue(failing.photos.photoFile("b.jpg").exists())
        assertFalse(failing.photos.importFolder.exists())
        assertNull(failing.settings.lastBackup.first())
    }

    @Test
    fun aRestoreClosesAnOpenUndoBarFirst() = runBlocking {
        val saved = seed(f)
        val bytes = soundBackup()
        val prepared = (f.backup.read { ByteArrayInputStream(bytes) } as ImportRead.Ready).prepared
        f.edits.deleteMeal(saved.meals[0])
        awaitTrue { f.edits.pending.value is PendingEdit.MealDeleted }

        assertTrue(f.backup.restore(prepared))

        assertNull(f.edits.pending.value)
        // The deleted meal's photo went with the bar, before the swap, so the restored a.jpg stays.
        assertTrue(f.photos.photoFile("a.jpg").exists())
        assertEquals(3, f.db.backupDao().mealCount())
    }

    @Test
    fun aBackupReadButNotRestoredIsDeleted() = runBlocking {
        val bytes = soundBackup()
        val read = f.backup.read { ByteArrayInputStream(bytes) }
        assertTrue(read is ImportRead.Ready)
        assertTrue(f.photos.importFolder.exists())

        f.backup.discard()

        assertFalse(f.photos.importFolder.exists())
        assertEquals(0, f.db.backupDao().mealCount())
    }

    private fun fixture(records: ((FoodJournalDatabase) -> BackupRecords)? = null) =
        BackupFixture(records = records).also { fixtures += it }

    /** Two places and three meals: two with photos, one with only a name. */
    private suspend fun seed(fixture: BackupFixture): Records {
        val noodles = fixture.db.placeDao().insert(Place(name = "林家乾麵", latitude = 25.03, longitude = 121.53))
        val cafe = fixture.db.placeDao().insert(Place(name = "Corner Café"))
        fixture.db.mealDao().insert(Meal(eatenAt = noon, photo = fixture.savePhoto("a.jpg"), price = 180, placeId = noodles))
        fixture.db.mealDao().insert(
            Meal(eatenAt = noon.plusHours(20), photo = fixture.savePhoto("b.jpg", Color.LTGRAY), name = "Latte", price = 0, isFavorite = true, placeId = cafe),
        )
        fixture.db.mealDao().insert(Meal(eatenAt = noon.minusDays(9), name = "Snack", price = 999_999, note = "Late"))
        return fixture.db.backupDao().snapshot()
    }

    /** The ZIP of a seeded phone, made on another fixture, as if exported from another phone. */
    private suspend fun soundBackup(): ByteArray {
        val other = fixture()
        seed(other)
        return ByteArrayOutputStream().also { other.backup.writeBackup(it) }.toByteArray()
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            for ((name, text) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
    }.toByteArray()

    /** [zip] again, without the entry [name]. */
    private fun withoutEntry(zip: ByteArray, name: String): ByteArray = rezip(zip, drop = name)

    /** [zip] again, with [extra] entries after its own. */
    private fun withEntries(zip: ByteArray, vararg extra: Pair<String, String>): ByteArray = rezip(zip, extra = extra.toList())

    private fun rezip(zip: ByteArray, drop: String? = null, extra: List<Pair<String, String>> = emptyList()): ByteArray {
        val source = File.createTempFile("source", ".zip", f.root).apply { writeBytes(zip) }
        return ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { target ->
                ZipFile(source).use { file ->
                    for (entry in file.entries()) {
                        if (entry.name == drop) continue
                        target.putNextEntry(ZipEntry(entry.name))
                        file.getInputStream(entry).use { it.copyTo(target) }
                        target.closeEntry()
                    }
                }
                for ((name, text) in extra) {
                    target.putNextEntry(ZipEntry(name))
                    target.write(text.toByteArray())
                    target.closeEntry()
                }
            }
        }.toByteArray()
    }
}
