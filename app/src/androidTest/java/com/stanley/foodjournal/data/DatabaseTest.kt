package com.stanley.foodjournal.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stanley.foodjournal.logic.LocationFix
import com.stanley.foodjournal.logic.mealLocation
import com.stanley.foodjournal.logic.prefillPlace
import com.stanley.foodjournal.ui.meal.MealDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private lateinit var db: FoodJournalDatabase
    private val meals get() = db.mealDao()
    private val places get() = db.placeDao()
    private val noon = LocalDateTime.of(2026, 11, 3, 12, 0)

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FoodJournalDatabase::class.java,
        ).build()
    }

    @After
    fun close() = db.close()

    @Test
    fun removesEmptyPlacesEvenWhenSomeMealsHaveNoPlace() = runBlocking {
        val used = places.insert(Place(name = "Noodles"))
        val emptyIds = (1..5).map { places.insert(Place(name = "Empty $it")) }
        meals.insert(meal(placeId = used))
        meals.insert(meal(placeId = null))

        assertEquals(5, places.deleteEmpty())
        assertEquals(listOf(used), places.all().map { it.id })
        assertNull(places.get(emptyIds.first()))
    }

    @Test
    fun keepsTheEmptyPlacesItIsToldTo() = runBlocking {
        val kept = places.insert(Place(name = "Kept"))
        places.insert(Place(name = "Gone"))

        places.deleteEmpty(keep = listOf(kept))

        assertEquals(listOf("Kept"), places.all().map { it.name })
    }

    @Test
    fun rejectsAPlaceNameThatDiffersOnlyInEnglishCase() {
        runBlocking { places.insert(Place(name = "Starbucks")) }
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { places.insert(Place(name = "starbucks")) }
        }
    }

    @Test
    fun savingWithANewPlaceReusesTheSavedPlaceOfThatName() = runBlocking {
        val cafe = places.insert(Place(name = "Café"))

        val id = meals.save(meal(), newPlace = Place(name = "CAFÉ", latitude = 25.0, longitude = 121.5))

        assertEquals(cafe, meals.get(id)?.placeId)
        assertEquals(1, places.all().size)
    }

    @Test
    fun savingWithANewPlaceCreatesIt() = runBlocking {
        val id = meals.save(meal(), newPlace = Place(name = "Dumplings", latitude = 25.0, longitude = 121.5))

        val place = places.all().single()
        assertEquals("Dumplings", place.name)
        assertEquals(25.0, place.latitude!!, 0.0)
        assertEquals(place.id, meals.get(id)?.placeId)
    }

    @Test
    fun savingAnExistingMealUpdatesIt() = runBlocking {
        val id = meals.save(meal(price = 100))

        meals.save(meals.get(id)!!.copy(price = 120, isFavorite = true))

        assertEquals(120, meals.get(id)?.price)
        assertEquals(true, meals.get(id)?.isFavorite)
    }

    @Test
    fun aDeletedMealComesBackWithItsId() = runBlocking {
        val id = meals.insert(meal(name = "Dumplings"))
        val saved = meals.get(id)!!

        meals.delete(saved)
        meals.insert(saved)

        assertEquals(saved, meals.get(id))
    }

    @Test
    fun recentNamesAreNewestFirstAndOncePerName() = runBlocking {
        meals.insert(meal(name = "Dumplings", eatenAt = noon.minusDays(3)))
        meals.insert(meal(name = "Beef noodles", eatenAt = noon.minusDays(2)))
        meals.insert(meal(name = "dumplings", eatenAt = noon.minusDays(1)))
        meals.insert(meal(name = null, eatenAt = noon))

        val names = meals.recentNames(limit = 10)

        assertEquals(2, names.size)
        assertEquals("beef noodles", names[1].lowercase())
    }

    @Test
    fun mergeMovesMealsAndCanBeUndone() = runBlocking {
        val awayId = places.insert(Place(name = "Lin Dong Fang", latitude = 25.05, longitude = 121.53))
        val survivorId = places.insert(Place(name = "Beef Noodles"))
        val m1 = meals.insert(meal(placeId = awayId))
        val m2 = meals.insert(meal(placeId = awayId))
        val other = meals.insert(meal(placeId = survivorId))
        val away = places.get(awayId)!!
        val survivorBefore = places.get(survivorId)!!

        val moved = places.merge(mergedAway = away, survivor = survivorBefore)

        assertEquals(setOf(m1, m2), moved.toSet())
        assertNull(places.get(awayId))
        assertEquals(survivorId, meals.get(m1)?.placeId)
        assertEquals(25.05, places.get(survivorId)?.latitude!!, 0.0)

        places.unmerge(away, survivorBefore, moved)

        assertEquals(away, places.get(awayId))
        assertEquals(survivorBefore, places.get(survivorId))
        assertEquals(awayId, meals.get(m2)?.placeId)
        assertEquals(survivorId, meals.get(other)?.placeId)
    }

    @Test
    fun placeSummariesCarryTheLastVisit() = runBlocking {
        val visited = places.insert(Place(name = "Visited"))
        places.insert(Place(name = "Never"))
        meals.insert(meal(placeId = visited, eatenAt = noon.minusDays(2)))
        meals.insert(meal(placeId = visited, eatenAt = noon))

        val summaries = places.observeSummaries().first().associateBy { it.place.name }

        assertEquals(noon, summaries.getValue("Visited").lastVisit)
        assertNull(summaries.getValue("Never").lastVisit)
    }

    @Test
    fun aNewPlaceTypedForACameraMealIsSavedAtThePhonesFix() = runBlocking {
        val draft = MealDraft(eatenAt = noon, photo = "a.jpg", priceText = "280", newPlaceName = "Lin Dong Fang")
        val fix = LocationFix(25.0526, 121.5395, accuracyMeters = 12f)

        val id = saveNew(draft, deviceFix = fix)

        val place = places.all().single()
        assertEquals(place.id, meals.get(id)?.placeId)
        assertEquals(25.0526, place.latitude!!, 0.0)
        assertEquals(121.5395, place.longitude!!, 0.0)
    }

    @Test
    fun aNewPlaceForAGalleryMealIsSavedAtThePhotosLocationOnly() = runBlocking {
        val draft = MealDraft(
            eatenAt = noon,
            photo = "a.jpg",
            priceText = "280",
            newPlaceName = "Night market",
            fromGallery = true,
            photoLatitude = 25.0478,
            photoLongitude = 121.5170,
        )
        val elsewhere = LocationFix(24.15, 120.67, accuracyMeters = 5f)

        saveNew(draft, deviceFix = elsewhere)
        saveNew(draft.copy(newPlaceName = "No location", photoLatitude = null, photoLongitude = null), elsewhere)

        val saved = places.all().associateBy { it.name }
        assertEquals(25.0478, saved.getValue("Night market").latitude!!, 0.0)
        assertNull(saved.getValue("No location").latitude)
    }

    @Test
    fun aNewPlaceFromAVagueFixIsSavedWithoutALocation() = runBlocking {
        val draft = MealDraft(eatenAt = noon, name = "Dumplings", priceText = "90", newPlaceName = "Dumplings")

        saveNew(draft, deviceFix = LocationFix(25.05, 121.53, accuracyMeters = 2_000f))

        assertNull(places.all().single().latitude)
    }

    @Test
    fun prefillFindsTheOnlySavedPlaceNearby() = runBlocking {
        // One thousandth of a degree of latitude is about 111 m.
        val near = places.insert(Place(name = "Noodles", latitude = 25.0337, longitude = 121.5654))
        places.insert(Place(name = "Coffee", latitude = 25.0370, longitude = 121.5654))
        places.insert(Place(name = "Market"))
        val here = LocationFix(25.0330, 121.5654, accuracyMeters = 10f)

        assertEquals(near, prefillPlace(places.all(), here)?.id)

        places.insert(Place(name = "Dumplings", latitude = 25.0325, longitude = 121.5654))
        assertNull(prefillPlace(places.all(), here))
    }

    @Test
    fun aMealWhosePlaceWasRemovedIsSavedWithoutOne() = runBlocking {
        val gone = places.insert(Place(name = "Gone"))
        places.deleteEmpty()

        val id = meals.save(meal(placeId = gone))

        assertNull(meals.get(id)?.placeId)
    }

    private suspend fun saveNew(draft: MealDraft, deviceFix: LocationFix?): Long {
        val location = mealLocation(isNew = true, draft.fromGallery, draft.photoLocation, deviceFix)
        return meals.save(draft.toMeal(0), newPlace = draft.newPlace(location))
    }

    @Test
    fun allMealsComeOldestFirstAndFollowChanges() = runBlocking {
        val later = meals.insert(meal(name = "Dinner", eatenAt = noon.plusHours(7)))
        val earlier = meals.insert(meal(name = "Lunch", eatenAt = noon))

        assertEquals(listOf(earlier, later), meals.observeAll().first().map { it.id })

        meals.delete(meals.get(earlier)!!)
        assertEquals(listOf(later), meals.observeAll().first().map { it.id })
    }

    private fun meal(
        name: String? = "Meal",
        price: Int = 100,
        placeId: Long? = null,
        eatenAt: LocalDateTime = noon,
    ) = Meal(eatenAt = eatenAt, name = name, price = price, placeId = placeId)
}
