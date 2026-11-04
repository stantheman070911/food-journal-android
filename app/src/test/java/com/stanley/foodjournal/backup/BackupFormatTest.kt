package com.stanley.foodjournal.backup

import com.stanley.foodjournal.data.Meal
import com.stanley.foodjournal.data.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class BackupFormatTest {
    private val noon = LocalDateTime.of(2026, 11, 3, 12, 30)
    private val places = listOf(Place(1, "林家乾麵", 25.03, 121.53), Place(2, "Corner Café"))
    private val meals = listOf(
        Meal(id = 4, eatenAt = noon, photo = "a.jpg", name = null, price = 180, placeId = 1),
        Meal(id = 7, eatenAt = noon.plusHours(8), name = "Latte", price = 0, isFavorite = true, note = "Oat", placeId = 2),
        Meal(id = 9, eatenAt = noon.minusDays(30), name = "Snack", price = 999_999),
        Meal(id = 11, eatenAt = noon.minusDays(2), name = "Dumplings", price = 120, latitude = 25.0418, longitude = 121.549),
    )
    private val backup = backupOf(meals, places, LocalDateTime.of(2026, 11, 4, 1, 15))

    @Test
    fun `a backup survives the trip to JSON and back`() {
        val read = BackupJson.decodeFromString<BackupFile>(BackupJson.encodeToString(BackupFile.serializer(), backup))

        assertEquals(backup, read)
        assertEquals(meals, read.restoredMeals())
        assertEquals(places, read.restoredPlaces())
        assertEquals(LocalDate.of(2026, 11, 4), read.exportDate())
    }

    @Test
    fun `the file carries its format and wall-clock times`() {
        val json = BackupJson.encodeToString(BackupFile.serializer(), backup)

        assertTrue(json.contains("\"format\": 2"))
        assertTrue(json.contains("\"eatenAt\": \"2026-11-03T12:30:00\""))
        assertTrue(json.contains("\"exportedAt\": \"2026-11-04T01:15:00\""))
    }

    @Test
    fun `fields a later version adds are ignored`() {
        val json = """{"format":1,"exportedAt":"2026-11-04T01:15:00","rating":5,"places":[],
            "meals":[{"id":1,"eatenAt":"2026-11-03T12:30:00","name":"Tea","price":50,"mood":"happy"}]}"""

        val read = BackupJson.decodeFromString<BackupFile>(json)

        assertEquals("Tea", read.meals.single().name)
        assertNull(checkBackup(read, emptySet()))
    }

    @Test
    fun `a sound backup passes`() {
        assertNull(checkBackup(backup, setOf("a.jpg")))
    }

    @Test
    fun `a newer format is refused, and no format is not a backup`() {
        assertEquals(BackupProblem.NewerVersion, checkBackup(backup.copy(format = 3), setOf("a.jpg")))
        assertEquals(BackupProblem.NotABackup, checkBackup(backup.copy(format = 0), setOf("a.jpg")))
    }

    @Test
    fun `a missing photo is damage`() {
        assertTrue(checkBackup(backup, emptySet()) is BackupProblem.Damaged)
    }

    @Test
    fun `every meal rule is checked`() {
        fun withMeal(meal: BackupMeal) = checkBackup(backup.copy(meals = backup.meals + meal), setOf("a.jpg"))
        val sound = BackupMeal(id = 20, eatenAt = "2026-11-01T08:00:00", name = "Toast", price = 60)

        assertNull(withMeal(sound))
        assertTrue(withMeal(sound.copy(price = -1)) is BackupProblem.Damaged)
        assertTrue(withMeal(sound.copy(price = 1_000_000)) is BackupProblem.Damaged)
        assertTrue(withMeal(sound.copy(name = "  ")) is BackupProblem.Damaged)
        assertTrue(withMeal(sound.copy(eatenAt = "Nov 1")) is BackupProblem.Damaged)
        assertTrue(withMeal(sound.copy(placeId = 3)) is BackupProblem.Damaged)
        assertTrue(withMeal(sound.copy(id = 4)) is BackupProblem.Damaged)
        assertTrue(withMeal(sound.copy(id = 0)) is BackupProblem.Damaged)
        assertNull(withMeal(sound.copy(latitude = 25.03, longitude = 121.53)))
        assertTrue(withMeal(sound.copy(latitude = 25.03)) is BackupProblem.Damaged)
        assertTrue(withMeal(sound.copy(longitude = 121.53)) is BackupProblem.Damaged)
        assertTrue(withMeal(sound.copy(latitude = 91.0, longitude = 121.53)) is BackupProblem.Damaged)
        assertTrue(withMeal(sound.copy(latitude = 25.03, longitude = 181.0)) is BackupProblem.Damaged)
    }

    @Test
    fun `a version 1 backup restores its meals without locations`() {
        val json = """{"format":1,"exportedAt":"2026-11-04T01:15:00","places":[],
            "meals":[{"id":1,"eatenAt":"2026-11-03T12:30:00","name":"Tea","price":50}]}"""

        val check = readBackup(json, emptySet())

        val meal = (check as BackupCheck.Passed).file.restoredMeals().single()
        assertNull(meal.latitude)
        assertNull(meal.longitude)
    }

    @Test
    fun `a version 2 backup restores where its meals were eaten`() {
        val json = BackupJson.encodeToString(BackupFile.serializer(), backup)

        val restored = (readBackup(json, setOf("a.jpg")) as BackupCheck.Passed).file.restoredMeals()

        assertEquals(25.0418, restored.single { it.id == 11L }.latitude)
        assertEquals(121.549, restored.single { it.id == 11L }.longitude)
    }

    @Test
    fun `places need unique ids and unique names, ignoring case and width`() {
        fun withPlace(place: BackupPlace) = checkBackup(backup.copy(places = backup.places + place), setOf("a.jpg"))

        assertNull(withPlace(BackupPlace(3, "Night market")))
        assertTrue(withPlace(BackupPlace(1, "Night market")) is BackupProblem.Damaged)
        assertTrue(withPlace(BackupPlace(3, "corner café")) is BackupProblem.Damaged)
        assertTrue(withPlace(BackupPlace(3, "ＣＯＲＮＥＲ Café")) is BackupProblem.Damaged)
        assertTrue(withPlace(BackupPlace(3, "   ")) is BackupProblem.Damaged)
    }

    @Test
    fun `only plain photo names unpack`() {
        assertEquals("1f3e.jpg", photoEntryName("photos/1f3e.jpg"))
        assertNull(photoEntryName("backup.json"))
        assertNull(photoEntryName("photos/"))
        assertNull(photoEntryName("photos/../databases/food-journal.db"))
        assertNull(photoEntryName("photos/sub/a.jpg"))
        assertNull(photoEntryName("photos/.."))
        assertNull(photoEntryName("photos/a\\b.jpg"))
        assertNull(photoEntryName("thumbs/a.jpg"))
    }

    @Test
    fun `reading checks the file and its photos`() {
        val json = BackupJson.encodeToString(BackupFile.serializer(), backup)

        assertEquals(BackupCheck.Passed(backup), readBackup(json, setOf("a.jpg")))
        assertTrue((readBackup(json, emptySet()) as BackupCheck.Failed).problem is BackupProblem.Damaged)
    }

    @Test
    fun `a file without backup json, or one that isn't ours, is not a backup`() {
        assertEquals(BackupCheck.Failed(BackupProblem.NotABackup), readBackup(null, emptySet()))
        assertEquals(BackupCheck.Failed(BackupProblem.NotABackup), readBackup("not json", emptySet()))
        assertEquals(BackupCheck.Failed(BackupProblem.NotABackup), readBackup("""{"name":"something else"}""", emptySet()))
        assertEquals(BackupCheck.Failed(BackupProblem.NotABackup), readBackup("""{"format":1,"meals":"none"}""", emptySet()))
    }

    @Test
    fun `a newer backup is newer even when its records no longer read`() {
        val json = """{"format":3,"exportedAt":"2026-11-04T01:15:00","records":{"meals":[]}}"""

        assertEquals(BackupCheck.Failed(BackupProblem.NewerVersion), readBackup(json, emptySet()))
    }

    @Test
    fun `export suggests a dated file name`() {
        assertEquals("food-journal-2026-11-04.zip", backupFileName(LocalDate.of(2026, 11, 4)))
    }
}
