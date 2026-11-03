package com.stanley.foodjournal.logic

import com.stanley.foodjournal.data.Meal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class WeeksTest {
    private val today = LocalDate.of(2026, 11, 3)

    @Test
    fun `weeks start on Monday`() {
        assertEquals(LocalDate.of(2026, 11, 2), weekStart(LocalDate.of(2026, 11, 2)))
        assertEquals(LocalDate.of(2026, 11, 2), weekStart(LocalDate.of(2026, 11, 8)))
        assertEquals(LocalDate.of(2026, 11, 9), weekStart(LocalDate.of(2026, 11, 9)))
    }

    @Test
    fun `meals group into weeks, newest week first, Monday to Sunday within`() {
        val sunday = meal(1, LocalDateTime.of(2026, 11, 8, 20, 0))
        val monday = meal(2, LocalDateTime.of(2026, 11, 2, 12, 0))
        val lastWeek = meal(3, LocalDateTime.of(2026, 10, 28, 19, 0))
        val nextMonday = meal(4, LocalDateTime.of(2026, 11, 9, 8, 0))

        val weeks = groupIntoWeeks(listOf(sunday, lastWeek, nextMonday, monday))

        assertEquals(listOf(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 10, 26)), weeks.map { it.start })
        assertEquals(listOf(2L, 1L), weeks[1].meals.map { it.id })
    }

    @Test
    fun `a late-night snack counts toward the day it was eaten`() {
        val snack = meal(1, LocalDateTime.of(2026, 11, 9, 1, 0))

        assertEquals(LocalDate.of(2026, 11, 9), groupIntoWeeks(listOf(snack)).single().start)
    }

    @Test
    fun `weeks without meals are left out`() {
        val weeks = groupIntoWeeks(
            listOf(meal(1, LocalDateTime.of(2026, 11, 3, 12, 0)), meal(2, LocalDateTime.of(2026, 10, 6, 12, 0))),
        )

        assertEquals(2, weeks.size)
        assertTrue(groupIntoWeeks(emptyList()).isEmpty())
    }

    @Test
    fun `a week's total adds its prices, free meals included`() {
        val week = groupIntoWeeks(
            listOf(
                meal(1, LocalDateTime.of(2026, 11, 3, 12, 0), price = 1_280),
                meal(2, LocalDateTime.of(2026, 11, 4, 12, 0), price = 0),
                meal(3, LocalDateTime.of(2026, 11, 5, 12, 0), price = 1_060),
            ),
        ).single()

        assertEquals(2_340, week.total)
        assertEquals("2,340", formatPrice(week.total))
    }

    @Test
    fun `meals at the same moment keep the order they were saved`() {
        val at = LocalDateTime.of(2026, 11, 3, 12, 0)
        assertEquals(listOf(4L, 9L), groupIntoWeeks(listOf(meal(9, at), meal(4, at))).single().meals.map { it.id })
    }

    @Test
    fun `week headers name both months only when the week crosses one`() {
        assertEquals("Oct 26 – Nov 1", formatWeekRange(LocalDate.of(2026, 10, 26), today))
        assertEquals("Nov 2 – 8", formatWeekRange(LocalDate.of(2026, 11, 2), today))
    }

    @Test
    fun `week headers show the year only outside the current year`() {
        assertEquals("Oct 20 – 26, 2025", formatWeekRange(LocalDate.of(2025, 10, 20), today))
        assertEquals("Sep 29 – Oct 5, 2025", formatWeekRange(LocalDate.of(2025, 9, 29), today))
        assertEquals("Dec 29, 2025 – Jan 4", formatWeekRange(LocalDate.of(2025, 12, 29), today))
        assertEquals("Dec 28 – Jan 3, 2027", formatWeekRange(LocalDate.of(2026, 12, 28), today))
        assertEquals("Dec 29, 2025 – Jan 4, 2026", formatWeekRange(LocalDate.of(2025, 12, 29), LocalDate.of(2027, 3, 1)))
    }

    @Test
    fun `tiles name the weekday`() {
        assertEquals("Mon", formatWeekday(LocalDate.of(2026, 11, 2)))
        assertEquals("Sun", formatWeekday(LocalDate.of(2026, 11, 8)))
    }

    private fun meal(id: Long, eatenAt: LocalDateTime, price: Int = 100) =
        Meal(id = id, eatenAt = eatenAt, name = "Meal $id", price = price)
}
