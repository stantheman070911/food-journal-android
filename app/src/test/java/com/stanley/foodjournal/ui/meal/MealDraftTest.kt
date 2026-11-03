package com.stanley.foodjournal.ui.meal

import com.stanley.foodjournal.data.Meal
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
}
