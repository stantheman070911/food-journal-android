package com.stanley.foodjournal.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/** Opens databases at the schemas exported to app/schemas/ and moves them to the current one. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FoodJournalDatabase::class.java)

    @Test
    fun version1MealsAndPlacesSurviveAndHaveNoLocation() = runBlocking {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL("INSERT INTO place (id, name, latitude, longitude) VALUES (3, '林家乾麵', 25.03, 121.53)")
            db.execSQL(
                "INSERT INTO meal (id, eatenAt, photo, name, price, isFavorite, note, placeId) " +
                    "VALUES (7, '2026-11-03T12:30:00', 'a.jpg', 'Noodles', 180, 1, 'Spicy', 3)",
            )
        }

        helper.runMigrationsAndValidate(DB, 2, true).close()

        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FoodJournalDatabase::class.java,
            DB,
        ).build()
        try {
            val meal = database.mealDao().get(7)!!
            assertEquals(LocalDateTime.of(2026, 11, 3, 12, 30), meal.eatenAt)
            assertEquals("Noodles", meal.name)
            assertEquals(3L, meal.placeId)
            assertNull(meal.latitude)
            assertNull(meal.longitude)
            assertEquals(25.03, database.placeDao().get(3)!!.latitude)
        } finally {
            database.close()
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
