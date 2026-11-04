package com.stanley.foodjournal.ui.meal

import com.stanley.foodjournal.data.Meal
import com.stanley.foodjournal.data.Place
import com.stanley.foodjournal.logic.LocationFix
import com.stanley.foodjournal.logic.LocationSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class MealDraftTest {
    private val meal = Meal(
        id = 7,
        eatenAt = LocalDateTime.of(2026, 11, 3, 12, 40),
        photo = "a.jpg",
        name = "Beef noodle soup",
        price = 280,
        isFavorite = true,
        note = "Extra spicy",
        placeId = 3,
    )

    @Test
    fun `a saved meal round trips through a draft`() {
        assertEquals(meal, MealDraft.from(meal).toMeal(meal.id))
    }

    @Test
    fun `an untouched draft has no changes`() {
        assertFalse(MealDraft.from(meal).hasChangesFrom(meal))
    }

    @Test
    fun `edits count as changes`() {
        val draft = MealDraft.from(meal)
        assertTrue(draft.copy(priceText = "300").hasChangesFrom(meal))
        assertTrue(draft.copy(isFavorite = false).hasChangesFrom(meal))
        assertTrue(draft.copy(newPlaceName = "Lin Dong Fang").hasChangesFrom(meal))
    }

    @Test
    fun `trailing spaces alone are not a change`() {
        assertFalse(MealDraft.from(meal).copy(name = "Beef noodle soup  ").hasChangesFrom(meal))
    }

    @Test
    fun `blank text saves as nothing`() {
        val saved = MealDraft(eatenAt = meal.eatenAt, photo = "a.jpg", name = "  ", note = "", priceText = "0")
            .toMeal(0)
        assertNull(saved.name)
        assertNull(saved.note)
        assertEquals(0, saved.price)
    }

    @Test
    fun `a new place leaves the place id for save to fill`() {
        assertNull(MealDraft.from(meal).copy(newPlaceName = "Lin Dong Fang").toMeal(meal.id).placeId)
    }

    @Test
    fun `save needs a photo or a name, and a price`() {
        val empty = MealDraft(eatenAt = meal.eatenAt)
        assertFalse(empty.canSave)
        assertFalse(empty.copy(priceText = "280").canSave)
        assertTrue(empty.copy(priceText = "280", name = "Dumplings").canSave)
        assertTrue(empty.copy(priceText = "0", photo = "a.jpg").canSave)
    }

    @Test
    fun `a new place is created where the meal is`() {
        val draft = MealDraft(eatenAt = meal.eatenAt, newPlaceName = "Lin Dong Fang")
        assertEquals(Place(name = "Lin Dong Fang", latitude = 25.05, longitude = 121.53), draft.newPlace(LocationFix(25.05, 121.53, 8f)))
        assertEquals(Place(name = "Lin Dong Fang"), draft.newPlace(null))
        assertNull(MealDraft(eatenAt = meal.eatenAt).newPlace(LocationFix(25.05, 121.53)))
    }

    @Test
    fun `a location needs both coordinates`() {
        val draft = MealDraft(eatenAt = meal.eatenAt, latitude = 25.05, longitude = 121.53)
        assertEquals(LocationFix(25.05, 121.53), draft.location)
        assertNull(draft.copy(longitude = null).location)
    }

    @Test
    fun `a saved meal's location round trips, with the switch on`() {
        val located = meal.copy(latitude = 25.05, longitude = 121.53)
        val draft = MealDraft.from(located)

        assertTrue(draft.saveLocation)
        assertEquals(LocationSource.Saved, draft.locationSource)
        assertEquals(located, draft.toMeal(located.id))
        assertFalse(draft.hasChangesFrom(located))
    }

    @Test
    fun `turning the switch off drops the location`() {
        val located = meal.copy(latitude = 25.05, longitude = 121.53)
        val off = MealDraft.from(located).copy(saveLocation = false, saveLocationTouched = true)

        assertTrue(off.hasChangesFrom(located))
        assertNull(off.toMeal(located.id).latitude)
        assertNull(off.toMeal(located.id).longitude)
    }

    @Test
    fun `the switch follows the location's source until it is tapped`() {
        val draft = MealDraft(eatenAt = meal.eatenAt)
        val here = LocationFix(25.05, 121.53)

        assertTrue(draft.withLocation(LocationSource.Camera, here).saveLocation)
        assertTrue(draft.withLocation(LocationSource.Gallery, here).saveLocation)
        assertFalse(draft.withLocation(LocationSource.Phone, here).saveLocation)
        val tapped = draft.withLocation(LocationSource.Phone, here).copy(saveLocation = true, saveLocationTouched = true)
        assertTrue(tapped.withLocation(LocationSource.Phone, LocationFix(25.06, 121.54)).saveLocation)
    }

    @Test
    fun `a location is saved only while the row shows`() {
        val draft = MealDraft(eatenAt = meal.eatenAt, photo = "a.jpg", priceText = "80")
            .withLocation(LocationSource.Gallery, LocationFix(25.05, 121.53))

        assertEquals(25.05, draft.toMeal(0).latitude!!, 0.0)
        assertNull(draft.toMeal(0, keepLocation = false).latitude)
    }
}
