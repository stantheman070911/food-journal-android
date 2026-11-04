package com.stanley.foodjournal.logic

import com.stanley.foodjournal.data.Meal
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class SearchTest {
    private val today = LocalDate.of(2026, 11, 4)
    private val places = mapOf(1L to "林家乾麵", 2L to "Corner Café")

    private val soup = meal(1, LocalDateTime.of(2026, 11, 2, 12, 0), name = "Beef noodle soup", place = 1)
    private val latte = meal(2, LocalDateTime.of(2026, 11, 3, 9, 0), name = "Latte", note = "Oat milk", place = 2, favorite = true)
    private val photoOnly = meal(3, LocalDateTime.of(2026, 10, 20, 19, 0), name = null, place = 1)
    private val snack = meal(4, LocalDateTime.of(2026, 11, 4, 1, 0), name = "ＢＵＢＢＬＥ tea")
    private val all = listOf(soup, latte, photoOnly, snack)

    @Test
    fun `with nothing typed, every meal is found, newest first`() {
        assertEquals(listOf(4L, 2L, 1L, 3L), ids(SearchFilter()))
    }

    @Test
    fun `meals at the same moment list the later saved first`() {
        val at = LocalDateTime.of(2026, 11, 1, 12, 0)
        val found = searchMeals(listOf(meal(5, at), meal(9, at)), places, SearchFilter())
        assertEquals(listOf(9L, 5L), found.map { it.id })
    }

    @Test
    fun `each word must appear somewhere, in any order`() {
        assertEquals(listOf(1L), ids(SearchFilter(text = "soup beef")))
        assertEquals(emptyList<Long>(), ids(SearchFilter(text = "beef latte")))
    }

    @Test
    fun `names, notes, and places are searched`() {
        assertEquals(listOf(2L), ids(SearchFilter(text = "oat")))
        assertEquals(listOf(2L), ids(SearchFilter(text = "corner")))
        assertEquals(listOf(1L, 3L), ids(SearchFilter(text = "乾麵")))
    }

    @Test
    fun `matching ignores case, width, and extra spaces`() {
        assertEquals(listOf(4L), ids(SearchFilter(text = "  bubble   TEA ")))
        assertEquals(listOf(2L), ids(SearchFilter(text = "café")))
        assertEquals(listOf(2L), ids(SearchFilter(text = "ＬＡＴＴＥ")))
    }

    @Test
    fun `a word never matches across two parts`() {
        // "soup" ends the name and "林" begins the place.
        assertEquals(emptyList<Long>(), ids(SearchFilter(text = "soup林")))
    }

    @Test
    fun `the place chip keeps meals at that place, and text filters within it`() {
        assertEquals(listOf(1L, 3L), ids(SearchFilter(placeId = 1)))
        assertEquals(listOf(1L), ids(SearchFilter(placeId = 1, text = "beef")))
    }

    @Test
    fun `the district chip keeps meals eaten there, and text filters within it`() {
        val districts = mapOf(1L to "63000030", 2L to "63000030", 4L to "65000010")
        fun find(filter: SearchFilter) = searchMeals(all, places, filter, districts).map { it.id }

        assertEquals(listOf(2L, 1L), find(SearchFilter(districtCode = "63000030")))
        assertEquals(listOf(1L), find(SearchFilter(districtCode = "63000030", text = "soup")))
        assertEquals(emptyList<Long>(), find(SearchFilter(districtCode = "10014020")))
    }

    @Test
    fun `dates include both days in full`() {
        val filter = SearchFilter(from = LocalDate.of(2026, 11, 2), to = LocalDate.of(2026, 11, 3))
        assertEquals(listOf(2L, 1L), ids(filter))
        // A 01:00 snack counts toward the day it was eaten.
        assertEquals(listOf(4L), ids(SearchFilter(from = today, to = today)))
    }

    @Test
    fun `favorites only`() {
        assertEquals(listOf(2L), ids(SearchFilter(favoritesOnly = true)))
    }

    @Test
    fun `filters combine`() {
        assertEquals(
            emptyList<Long>(),
            ids(SearchFilter(placeId = 2, favoritesOnly = true, to = LocalDate.of(2026, 11, 2))),
        )
    }

    @Test
    fun `result dates show the year only outside this one`() {
        assertEquals("Tue, Nov 3", formatResultDate(LocalDate.of(2026, 11, 3), today))
        assertEquals("Mon, Nov 3, 2025", formatResultDate(LocalDate.of(2025, 11, 3), today))
    }

    @Test
    fun `date ranges are written as week headers are`() {
        assertEquals("Oct 1 – Nov 4", formatDateRange(LocalDate.of(2026, 10, 1), today, today))
        assertEquals("Nov 1 – 4", formatDateRange(LocalDate.of(2026, 11, 1), today, today))
        assertEquals("Nov 4", formatDateRange(today, today, today))
        assertEquals("Nov 4, 2025", formatDateRange(LocalDate.of(2025, 11, 4), LocalDate.of(2025, 11, 4), today))
        assertEquals("Dec 1, 2025 – Nov 4", formatDateRange(LocalDate.of(2025, 12, 1), today, today))
    }

    private fun ids(filter: SearchFilter) = searchMeals(all, places, filter).map { it.id }

    private fun meal(
        id: Long,
        eatenAt: LocalDateTime,
        name: String? = "Meal $id",
        note: String? = null,
        place: Long? = null,
        favorite: Boolean = false,
    ) = Meal(id = id, eatenAt = eatenAt, name = name, note = note, price = 100, placeId = place, isFavorite = favorite)
}
