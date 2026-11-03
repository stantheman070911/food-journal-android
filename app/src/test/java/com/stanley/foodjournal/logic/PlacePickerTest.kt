package com.stanley.foodjournal.logic

import com.stanley.foodjournal.data.Place
import com.stanley.foodjournal.data.PlaceSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PlacePickerTest {
    private val here = LocationFix(25.0330, 121.5654, accuracyMeters = 10f)
    private val noodles = Place(1, "Noodles", 25.0330 + 0.00072, 121.5654)
    private val coffee = Place(2, "Simple Kaffa", 25.0330 + 0.0108, 121.5654)
    private val market = Place(3, "Night market")
    private val places = listOf(summary(market, 1), summary(coffee, 2), summary(noodles, 3))

    @Test
    fun `lists every place nearest first with distances`() {
        val content = content()
        assertEquals(listOf(1L, 2L, 3L), content.places.map { it.place.id })
        assertEquals(listOf("80 m", "1.2 km", null), content.places.map { it.distance })
    }

    @Test
    fun `without a location, lists recent visits first and shows no distances`() {
        val content = content(location = null)
        assertEquals(listOf(3L, 2L, 1L), content.places.map { it.place.id })
        assertTrue(content.places.all { it.distance == null })
    }

    @Test
    fun `a vague fix shows no distances`() {
        assertTrue(content(location = here.copy(accuracyMeters = 500f)).places.all { it.distance == null })
    }

    @Test
    fun `typing filters the places`() {
        assertEquals(listOf(2L), content(query = "kaf").places.map { it.place.id })
        assertEquals(listOf(2L), content(query = "ＫＡＦ").places.map { it.place.id })
    }

    @Test
    fun `a new name is offered to add, cleaned`() {
        assertEquals("Lin Dong Fang", content(query = "  Lin   Dong Fang ").addName)
    }

    @Test
    fun `a saved name is not offered again, whatever its case`() {
        assertNull(content(query = "noodles").addName)
        assertNull(content(query = " NOODLES ").addName)
    }

    @Test
    fun `a partial match is still offered as new`() {
        val content = content(query = "Noodle")
        assertEquals("Noodle", content.addName)
        assertEquals(listOf(1L), content.places.map { it.place.id })
    }

    @Test
    fun `nothing typed offers nothing to add and shows No place`() {
        val content = content(query = "   ")
        assertNull(content.addName)
        assertTrue(content.showNoPlace)
        assertFalse(content(query = "N").showNoPlace)
    }

    @Test
    fun `the chosen place is checked`() {
        val content = content(selectedPlaceId = 2)
        assertEquals(listOf(false, true, false), content.places.map { it.isSelected })
        assertFalse(content.noPlaceSelected)
        assertTrue(content().noPlaceSelected)
    }

    @Test
    fun `a pending new place shows first and is not offered again`() {
        val content = content(newPlaceName = "Lin Dong Fang")
        assertEquals("Lin Dong Fang", content.pendingName)
        assertFalse(content.noPlaceSelected)
        assertTrue(content.places.none { it.isSelected })
        assertNull(content(query = "lin dong fang", newPlaceName = "Lin Dong Fang").addName)
        assertNull(content(query = "kaffa", newPlaceName = "Lin Dong Fang").pendingName)
    }

    @Test
    fun `a pending name a saved place has taken checks that place instead`() {
        val content = content(newPlaceName = "NOODLES")
        assertNull(content.pendingName)
        assertEquals(listOf(true, false, false), content.places.map { it.isSelected })
    }

    @Test
    fun `the check key picks a saved place typed in full`() {
        assertEquals(PickerSubmit.Select(2), pickerSubmit(content(query = "simple kaffa"), "simple kaffa"))
    }

    @Test
    fun `the check key adds a new name`() {
        assertEquals(PickerSubmit.Add("Lin Dong Fang"), pickerSubmit(content(query = "Lin Dong Fang "), "Lin Dong Fang "))
        assertEquals(PickerSubmit.Add("Noodle"), pickerSubmit(content(query = "Noodle"), "Noodle"))
    }

    @Test
    fun `the check key keeps a pending place typed again`() {
        val query = "lin dong fang"
        assertEquals(
            PickerSubmit.Add("Lin Dong Fang"),
            pickerSubmit(content(query = query, newPlaceName = "Lin Dong Fang"), query),
        )
    }

    @Test
    fun `the check key does nothing with nothing typed`() {
        assertEquals(PickerSubmit.Nothing, pickerSubmit(content(query = "  "), "  "))
    }

    private fun content(
        query: String = "",
        location: LocationFix? = here,
        selectedPlaceId: Long? = null,
        newPlaceName: String? = null,
    ) = placePickerContent(places, query, location, selectedPlaceId, newPlaceName)

    private fun summary(place: Place, daysAgo: Long) =
        PlaceSummary(place, LocalDateTime.of(2026, 11, 3, 12, 0).minusDays(daysAgo))
}
