package com.stanley.foodjournal.logic

import com.stanley.foodjournal.data.Meal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class RecapTest {
    // A Wednesday: the current week is Nov 2 – 8.
    private val today = LocalDate.of(2026, 11, 4)
    private val week = Period(PeriodKind.Week, LocalDate.of(2026, 11, 2))
    private val october = Period(PeriodKind.Month, LocalDate.of(2026, 10, 1))

    @Test
    fun `a period with few meals shows them all, in the order eaten`() {
        val meals = listOf(meal(3, day = 5), meal(1, day = 3), meal(2, day = 4))
        assertEquals(listOf(1L, 2L, 3L), recapMeals(meals).map { it.id })
    }

    @Test
    fun `a busy period shows every meal, favorite or not`() {
        val meals = (1000L downTo 1L).map { meal(it, minute = it.toInt(), favorite = it % 7 == 0L) }
        assertEquals((1L..1000L).toList(), recapMeals(meals).map { it.id })
    }

    @Test
    fun `the collage covers its area with no gaps or overlaps`() {
        for (count in (1..60) + listOf(97, 365, 1_000)) {
            val tiles = collageTiles(count, width = 1000f, height = 1440f, gap = 6f)
            assertEquals(count, tiles.size)
            val rows = tiles.groupBy { it.top }
            // Each row runs edge to edge, and the last row ends at the bottom.
            rows.values.forEach { row ->
                assertEquals(0f, row.first().left)
                assertEquals(1000f, row.last().right)
            }
            assertEquals(1440f, tiles.maxOf { it.bottom })
            val area = tiles.sumOf { ((it.right - it.left) * (it.bottom - it.top)).toDouble() }
            val gaps = 1000.0 * 1440 - area
            assertTrue("$count tiles leave $gaps px uncovered", gaps < 6.0 * (1000 + 1440) * count)
            // Fuller rows come first, and no two rows differ by more than one tile.
            val sizes = rows.values.map { it.size }
            assertEquals(sizes.sortedDescending(), sizes)
            assertTrue(sizes.max() - sizes.min() <= 1)
        }
    }

    @Test
    fun `one meal fills the collage, and many keep their tiles near portrait`() {
        assertEquals(listOf(TileRect(0f, 0f, 1000f, 1440f)), collageTiles(1, 1000f, 1440f, 6f))
        for (count in listOf(24, 365, 1_000)) {
            val tile = collageTiles(count, 1000f, 1440f, 2f).first()
            val aspect = (tile.right - tile.left) / (tile.bottom - tile.top)
            assertTrue("$count meals: aspect $aspect", aspect in 0.6f..1.0f)
        }
    }

    @Test
    fun `the words name the period, its meals, places, and total`() {
        val meals = listOf(meal(1, day = 3, price = 127, place = 1), meal(2, day = 3, price = 80, place = 2), meal(3, day = 4, price = 228))
        val text = recapText(week, meals, previousMeals = emptyList(), today)

        assertEquals("Nov 2 – 8", text.title)
        assertEquals("2026", text.year)
        assertEquals("3 meals · 2 places · 435", text.stats)
        // Without meals the week before, there is nothing to compare with.
        assertNull(text.comparison)
    }

    @Test
    fun `one meal and no places read in the singular, with no place count`() {
        assertEquals("1 meal · 1,200", recapText(week, listOf(meal(1, day = 3, price = 1_200)), emptyList(), today).stats)
    }

    @Test
    fun `each figure can be left off`() {
        val meals = listOf(meal(1, day = 2, price = 180, place = 1), meal(2, day = 2, price = 20))
        val lastWeek = listOf(meal(10, day = -4, price = 300))
        fun text(options: RecapOptions) = recapText(week, meals, lastWeek, today, options)

        assertEquals("2 meals · 1 place · 200", text(RecapOptions()).stats)
        assertEquals("1 place · 200", text(RecapOptions(meals = false)).stats)
        assertEquals("2 meals · 200", text(RecapOptions(places = false)).stats)
        assertEquals("2 meals · 1 place", text(RecapOptions(total = false)).stats)
        assertNull(text(RecapOptions(meals = false, places = false, total = false)).stats)
        assertEquals("100 less than last week", text(RecapOptions(total = false)).comparison)
        assertNull(text(RecapOptions(comparison = false)).comparison)
        // The title and year always show.
        assertEquals("Nov 2 – 8" to "2026", text(RecapOptions(false, false, false, false)).let { it.title to it.year })
    }

    @Test
    fun `titles for a month, a year, and a week across New Year`() {
        assertEquals("October" to "2026", recapText(october, listOf(meal(1)), emptyList(), today).let { it.title to it.year })
        val year = Period(PeriodKind.Year, LocalDate.of(2026, 1, 1))
        assertEquals("2026" to null, recapText(year, listOf(meal(1)), emptyList(), today).let { it.title to it.year })
        val newYear = Period(PeriodKind.Week, LocalDate.of(2025, 12, 29))
        assertEquals("Dec 29 – Jan 4" to "2025 – 26", recapText(newYear, listOf(meal(1)), emptyList(), today).let { it.title to it.year })
    }

    @Test
    fun `the comparison states the difference plainly`() {
        val lastWeek = listOf(meal(10, day = -4, price = 300))
        assertEquals("120 less than last week", recapText(week, listOf(meal(1, day = 2, price = 180)), lastWeek, today).comparison)
        assertEquals("50 more than last week", recapText(week, listOf(meal(1, day = 2, price = 350)), lastWeek, today).comparison)
        assertEquals("The same as last week", recapText(week, listOf(meal(1, day = 2, price = 300)), lastWeek, today).comparison)

        val september = listOf(meal(20, eatenAt = LocalDateTime.of(2026, 9, 10, 12, 0), price = 1_000))
        val octoberMeals = listOf(meal(21, eatenAt = LocalDateTime.of(2026, 10, 10, 12, 0), price = 2_500))
        assertEquals("1,500 more than Sep", recapText(october, octoberMeals, september, today).comparison)
    }

    @Test
    fun `an unfinished week is compared over the same days, as Spending does`() {
        // Today is Wednesday, so last week counts only Monday to Wednesday.
        val lastWeek = listOf(meal(10, day = -6, price = 200), meal(11, day = -1, price = 900))
        assertEquals("100 less than last week", recapText(week, listOf(meal(1, day = 2, price = 100)), lastWeek, today).comparison)
        // Last week's days so far cost nothing, so there is no comparison.
        assertNull(recapText(week, listOf(meal(1, day = 2)), listOf(meal(11, day = -1)), today).comparison)
    }

    @Test
    fun `an earlier week compares with the week before it`() {
        val earlier = Period(PeriodKind.Week, LocalDate.of(2026, 10, 26))
        val before = listOf(meal(10, eatenAt = LocalDateTime.of(2026, 10, 20, 12, 0), price = 100))
        val meals = listOf(meal(1, eatenAt = LocalDateTime.of(2026, 10, 27, 12, 0), price = 100))
        assertEquals("The same as the week before", recapText(earlier, meals, before, today).comparison)
    }

    /** The recap's words with the figures [options] keeps, all of them by default. */
    private fun recapText(
        period: Period,
        meals: List<Meal>,
        previousMeals: List<Meal>,
        today: LocalDate,
        options: RecapOptions = RecapOptions(),
    ) = recapFigures(period, meals, previousMeals, today).text(options)

    /** A meal [day] days after Nov 1, 2026, or at [eatenAt]. */
    private fun meal(
        id: Long,
        day: Int = 3,
        minute: Int = 0,
        price: Int = 100,
        place: Long? = null,
        favorite: Boolean = false,
        eatenAt: LocalDateTime = LocalDateTime.of(2026, 11, 1, 12, 0).plusDays(day.toLong()).plusMinutes(minute.toLong()),
    ) = Meal(id = id, eatenAt = eatenAt, name = "Meal $id", price = price, placeId = place, isFavorite = favorite)
}
