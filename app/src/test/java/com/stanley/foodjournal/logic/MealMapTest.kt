package com.stanley.foodjournal.logic

import com.stanley.foodjournal.data.Meal
import com.stanley.foodjournal.data.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class MealMapTest {
    private val noon = LocalDateTime.of(2026, 11, 3, 12, 0)

    // Two "districts": everything west of 121.5 is W, the rest up to 122 is E. Beyond is outside.
    private val codeAt: (Double, Double) -> String? = { _, longitude ->
        when {
            longitude < 121.5 -> "W"
            longitude < 122.0 -> "E"
            else -> null
        }
    }

    private val noodles = Place(1, "Noodles", 25.03, 121.4)
    private val cafe = Place(2, "Cafe", 25.04, 121.7)
    private val market = Place(3, "Market")
    private val tokyo = Place(4, "Tokyo Ramen", 35.68, 139.76)
    private val places = listOf(noodles, cafe, market, tokyo)

    private fun meal(id: Long, placeId: Long? = null, latitude: Double? = null, longitude: Double? = null) =
        Meal(id = id, eatenAt = noon, name = "Meal $id", price = 100, placeId = placeId, latitude = latitude, longitude = longitude)

    @Test
    fun `a meal is where it was eaten, or else where its place is`() {
        val byId = places.associateBy { it.id }

        assertEquals(LocationFix(25.1, 121.6), mealPosition(meal(1, placeId = 1, latitude = 25.1, longitude = 121.6), byId))
        assertEquals(LocationFix(25.03, 121.4), mealPosition(meal(2, placeId = 1), byId))
        assertNull(mealPosition(meal(3, placeId = 3), byId))
        assertNull(mealPosition(meal(4), byId))
    }

    @Test
    fun `each place gets one pin counting all its meals`() {
        val map = mealMap(listOf(meal(1, 1), meal(2, 1), meal(3, 2)), places, codeAt) { it.name!! }

        assertEquals(
            listOf(MapPin(1, null, "Noodles", 25.03, 121.4, 2), MapPin(2, null, "Cafe", 25.04, 121.7, 1)),
            map.pins.sortedBy { it.placeId },
        )
        assertEquals(mapOf("W" to 2, "E" to 1), map.mealsByDistrict)
    }

    @Test
    fun `a meal with a location and no place, or a place without coordinates, gets its own small pin`() {
        val map = mealMap(
            listOf(meal(1, latitude = 25.0, longitude = 121.45), meal(2, placeId = 3, latitude = 25.0, longitude = 121.8), meal(3, placeId = 3)),
            places,
            codeAt,
        ) { it.name!! }

        assertEquals(listOf(1L, 2L), map.pins.map { it.mealId })
        assertTrue(map.pins.all { it.placeId == null && it.meals == 1 })
        assertEquals("Meal 1", map.pins.first().name)
    }

    @Test
    fun `meals outside Taiwan have no pin and are counted`() {
        val map = mealMap(
            listOf(meal(1, placeId = 4), meal(2, placeId = 4), meal(3, latitude = 10.0, longitude = 130.0), meal(4, placeId = 1)),
            places,
            codeAt,
        ) { it.name!! }

        assertEquals(3, map.outsideTaiwan)
        assertEquals(listOf<Long?>(1), map.pins.map { it.placeId })
        assertFalse(map.isEmpty)
    }

    @Test
    fun `a meal's own location decides its district`() {
        val districts = mealDistricts(
            listOf(meal(1, placeId = 1, latitude = 25.0, longitude = 121.8), meal(2, placeId = 1), meal(3), meal(4, placeId = 4)),
            places.associateBy { it.id },
            codeAt,
        )

        assertEquals(mapOf(1L to "E", 2L to "W"), districts)
    }

    @Test
    fun `no positions at all is the empty map`() {
        assertTrue(mealMap(listOf(meal(1), meal(2, placeId = 3)), places, codeAt) { "" }.isEmpty)
    }

    @Test
    fun `a label that would cover one already kept is left out`() {
        val boxes = listOf(
            LabelBox(0f, 0f, 100f, 20f),
            LabelBox(90f, 10f, 190f, 30f), // Overlaps the first.
            LabelBox(100f, 0f, 200f, 20f), // Touches the first's edge only.
            LabelBox(150f, 15f, 250f, 35f), // Overlaps the third, which was kept.
            LabelBox(0f, 40f, 100f, 60f),
        )

        assertEquals(listOf(0, 2, 4), labelsThatFit(boxes))
    }

    @Test
    fun `the projection round trips and fits the opening frame`() {
        val x = TaiwanProjection.x(121.5)
        val y = TaiwanProjection.y(25.0)
        assertEquals(121.5, TaiwanProjection.longitude(x), 1e-9)
        assertEquals(25.0, TaiwanProjection.latitude(y), 1e-9)
        assertTrue("North is up", TaiwanProjection.y(25.3) < TaiwanProjection.y(22.0))

        val opening = TaiwanProjection.Opening
        val scale = TaiwanProjection.fit(opening, width = 1080f, height = 1800f)
        val width = (TaiwanProjection.x(opening.east) - TaiwanProjection.x(opening.west)) * scale
        val height = (TaiwanProjection.y(opening.south) - TaiwanProjection.y(opening.north)) * scale
        assertTrue(width <= 1080.001 && height <= 1800.001)
        assertTrue(width >= 1079.999 || height >= 1799.999)
    }
}
