package com.stanley.foodjournal.logic

import com.stanley.foodjournal.data.Place
import com.stanley.foodjournal.data.PlaceSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PlacesTest {
    // One thousandth of a degree of latitude is about 111 m.
    private val here = LocationFix(25.0330, 121.5654, accuracyMeters = 10f)
    private val at80m = Place(1, "Noodles", 25.0330 + 0.00072, 121.5654)
    private val at140m = Place(2, "Dumplings", 25.0330 - 0.00126, 121.5654)
    private val at400m = Place(3, "Coffee", 25.0330 + 0.0036, 121.5654)
    private val noLocation = Place(4, "Market")

    @Test
    fun `measures distance on the earth`() {
        assertEquals(111.2, distanceMeters(25.0, 121.5, 25.001, 121.5), 0.5)
        assertEquals(0.0, distanceMeters(25.0, 121.5, 25.0, 121.5), 0.0)
    }

    @Test
    fun `prefills the only place within 150 m`() {
        assertEquals(at80m, prefillPlace(listOf(at80m, at400m, noLocation), here))
    }

    @Test
    fun `prefills nothing when two places are nearby`() {
        assertNull(prefillPlace(listOf(at80m, at140m, at400m), here))
    }

    @Test
    fun `prefills nothing when no place is nearby`() {
        assertNull(prefillPlace(listOf(at400m, noLocation), here))
        assertNull(prefillPlace(emptyList(), here))
    }

    @Test
    fun `prefills nothing from a vague fix or no fix`() {
        assertNull(prefillPlace(listOf(at80m), here.copy(accuracyMeters = 120f)))
        assertNull(prefillPlace(listOf(at80m), null))
    }

    @Test
    fun `trusts a photo's location, which has no accuracy`() {
        assertEquals(at80m, prefillPlace(listOf(at80m, at400m), here.copy(accuracyMeters = null)))
    }

    @Test
    fun `150 m is still nearby`() {
        val edge = Place(5, "Edge", 25.0330 + 0.0013485, 121.5654)
        assertEquals(edge, prefillPlace(listOf(edge), here))
    }

    @Test
    fun `picker lists nearest first, then places without a location`() {
        val places = listOf(summary(noLocation, 1), summary(at400m, 2), summary(at80m, 3), summary(at140m, 4))
        assertEquals(listOf(1L, 2L, 3L, 4L), orderForPicker(places, here).map { it.place.id })
    }

    @Test
    fun `picker lists most recent visits first without a fix`() {
        val places = listOf(summary(at80m, daysAgo = 5), summary(at400m, daysAgo = 1), summary(noLocation, null))
        assertEquals(listOf(3L, 1L, 4L), orderForPicker(places, null).map { it.place.id })
    }

    @Test
    fun `formats distances for the picker`() {
        assertEquals("80 m", formatDistance(78.0))
        assertEquals("1.2 km", formatDistance(1_240.0))
        assertEquals("15 km", formatDistance(15_400.0))
        assertEquals("1,240 km", formatDistance(1_240_000.0))
    }

    @Test
    fun `blank or identical names change nothing`() {
        val places = listOf(at80m, at400m)
        assertEquals(RenamePlan.Unchanged, planRename(places, at80m, "  "))
        assertEquals(RenamePlan.Unchanged, planRename(places, at80m, " Noodles "))
    }

    @Test
    fun `changing only the capitalization renames instead of merging with itself`() {
        assertEquals(RenamePlan.Rename("NOODLES"), planRename(listOf(at80m, at400m), at80m, "NOODLES"))
    }

    @Test
    fun `renaming to another place's name merges into it`() {
        val places = listOf(at80m, at400m)
        assertEquals(RenamePlan.Merge(into = at400m), planRename(places, at80m, "coffee"))
        assertEquals(RenamePlan.Merge(into = at400m), planRename(places, at80m, "Ｃｏｆｆｅｅ"))
    }

    @Test
    fun `renaming cleans the new name`() {
        assertEquals(RenamePlan.Rename("Beef Noodles"), planRename(listOf(at80m), at80m, " Beef   Noodles "))
    }

    @Test
    fun `the survivor keeps its own coordinates`() {
        assertEquals(at400m, mergedPlace(survivor = at400m, mergedAway = at80m))
    }

    @Test
    fun `the survivor takes the other's coordinates when it has none`() {
        val merged = mergedPlace(survivor = noLocation, mergedAway = at80m)
        assertEquals(Place(4, "Market", at80m.latitude, at80m.longitude), merged)
    }

    @Test
    fun `the switch starts off only for the phone's location while typing`() {
        assertTrue(savesLocationByDefault(LocationSource.Camera))
        assertTrue(savesLocationByDefault(LocationSource.Gallery))
        assertTrue(savesLocationByDefault(LocationSource.Saved))
        assertFalse(savesLocationByDefault(LocationSource.Phone))
    }

    @Test
    fun `the row shows once there is a location, and for a new meal only while location is allowed`() {
        val here = LocationFix(25.04, 121.55)
        assertTrue(showsLocationRow(isNew = true, location = here, locationAllowed = true))
        assertFalse(showsLocationRow(isNew = true, location = here, locationAllowed = false))
        assertFalse(showsLocationRow(isNew = true, location = null, locationAllowed = true))
        assertTrue(showsLocationRow(isNew = false, location = here, locationAllowed = false))
        assertFalse(showsLocationRow(isNew = false, location = null, locationAllowed = true))
    }

    @Test
    fun `places use the saved location, and none with the switch off`() {
        val here = LocationFix(25.04, 121.55)
        for (source in LocationSource.entries) {
            assertEquals(here, placeLocation(true, source, here, saveLocation = true, locationAllowed = true))
            assertNull(placeLocation(true, source, here, saveLocation = false, locationAllowed = true))
        }
        assertEquals(here, placeLocation(false, LocationSource.Saved, here, saveLocation = true, locationAllowed = false))
    }

    @Test
    fun `without location allowed, only a new gallery meal's photo places it`() {
        val photo = LocationFix(25.04, 121.55)
        assertEquals(photo, placeLocation(true, LocationSource.Gallery, photo, saveLocation = false, locationAllowed = false))
        assertNull(placeLocation(true, LocationSource.Gallery, null, saveLocation = true, locationAllowed = false))
        assertNull(placeLocation(true, LocationSource.Camera, photo, saveLocation = true, locationAllowed = false))
        assertNull(placeLocation(true, null, null, saveLocation = false, locationAllowed = false))
    }

    @Test
    fun `a photo's location is used as it is`() {
        assertEquals(LocationFix(25.0331, 121.5297), photoLocation(25.0331, 121.5297))
        assertEquals(LocationFix(-33.86, 0.0), photoLocation(-33.86, 0.0))
    }

    @Test
    fun `a zeroed or impossible photo location counts as none`() {
        assertNull(photoLocation(0.0, 0.0))
        assertNull(photoLocation(91.0, 121.5))
        assertNull(photoLocation(25.0, Double.NaN))
    }

    private fun summary(place: Place, daysAgo: Long?) = PlaceSummary(
        place = place,
        lastVisit = daysAgo?.let { LocalDateTime.of(2026, 11, 3, 12, 0).minusDays(it) },
    )
}
