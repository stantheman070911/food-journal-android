package com.stanley.foodjournal.logic

import com.stanley.foodjournal.data.Meal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class RemindersTest {
    private val today = LocalDate.of(2026, 11, 26)

    @Test
    fun `a late block's small hours belong to the evening before`() {
        assertEquals(BlockDay(TimeBand.Late, LocalDate.of(2026, 11, 25)), blockDayOf(LocalDateTime.of(2026, 11, 26, 0, 30)))
        assertEquals(BlockDay(TimeBand.Late, LocalDate.of(2026, 11, 26)), blockDayOf(LocalDateTime.of(2026, 11, 26, 21, 0)))
        assertEquals(BlockDay(TimeBand.Breakfast, today), blockDayOf(LocalDateTime.of(2026, 11, 26, 5, 0)))
    }

    @Test
    fun `a block eaten in on most days has the median of each day's first time`() {
        // Lunch on 20 of the last 28 days, at 12:00 to 12:19; a second lunch never counts.
        val lunches = (1..20).map { daysAgo -> meal(today.minusDays(daysAgo.toLong()).atTime(12, daysAgo - 1)) } +
            meal(today.minusDays(1).atTime(13, 30))
        assertEquals(mapOf(TimeBand.Lunch to LocalTime.of(12, 9)), usualTimes(lunches, today))
    }

    @Test
    fun `a block eaten in on fewer than half the days has no usual time`() {
        val sometimes = (1..28 step 3).map { meal(today.minusDays(it.toLong()).atTime(15, 0)) } +
            (1..28).map { meal(today.minusDays(it.toLong()).atTime(19, 0)) }
        assertEquals(setOf(TimeBand.Dinner), usualTimes(sometimes, today).keys)
    }

    @Test
    fun `half the days since the first meal is enough while history is short`() {
        // Ten days of history: dinner on five of them.
        val meals = listOf(meal(today.minusDays(10).atTime(8, 0))) +
            (1..5).map { meal(today.minusDays(it.toLong() * 2).atTime(19, 30)) }
        assertEquals(LocalTime.of(19, 30), usualTimes(meals, today)[TimeBand.Dinner])
    }

    @Test
    fun `nothing is usual with under a week of history, and today never counts`() {
        val meals = (0..5).map { meal(today.minusDays(it.toLong()).atTime(12, 0)) }
        assertTrue(usualTimes(meals, today).isEmpty())
    }

    @Test
    fun `a late block's median runs across midnight`() {
        // Each evening's first late entry is 23:50, before the 00:20 that follows it.
        val meals = (1..14).map { meal(today.minusDays(it.toLong()).atTime(23, 50)) } +
            (1..14).map { meal(today.minusDays(it.toLong() - 1).atTime(0, 20)) }
        assertEquals(LocalTime.of(23, 50), usualTimes(meals, today)[TimeBand.Late])
        // Ten evenings at 00:40 and four at 22:00: the middle falls after midnight.
        val pastMidnight = (1..14).map { meal(today.minusDays(it.toLong() - 1).atTime(0, 40)) } +
            (11..14).map { meal(today.minusDays(it.toLong()).atTime(22, 0)) }
        assertEquals(LocalTime.of(0, 40), usualTimes(pastMidnight, today)[TimeBand.Late])
        // Seven and seven: the mean of the middle two, 22:00 and 00:40.
        val evenSplit = (1..14).map { meal(today.minusDays(it.toLong() - 1).atTime(0, 40)) } +
            (8..14).map { meal(today.minusDays(it.toLong()).atTime(22, 0)) }
        assertEquals(LocalTime.of(23, 20), usualTimes(evenSplit, today)[TimeBand.Late])
    }

    @Test
    fun `the next reminder is an hour after the soonest usual time not yet logged`() {
        val usual = mapOf(TimeBand.Lunch to LocalTime.of(12, 30), TimeBand.Dinner to LocalTime.of(19, 0))
        val morning = today.atTime(9, 0)

        assertEquals(
            Reminder(BlockDay(TimeBand.Lunch, today), today.atTime(13, 30), LocalTime.of(12, 30)),
            nextReminder(emptyList(), usual, morning),
        )
        // Lunch is logged, so dinner is next.
        val lunch = listOf(meal(today.atTime(12, 10)))
        assertEquals(today.atTime(20, 0), nextReminder(lunch, usual, morning)!!.at)
        // After dinner's time, tomorrow's lunch.
        assertEquals(today.plusDays(1).atTime(13, 30), nextReminder(lunch, usual, today.atTime(20, 0))!!.at)
    }

    @Test
    fun `a late reminder past midnight falls on the next calendar day`() {
        val usual = mapOf(TimeBand.Late to LocalTime.of(23, 45))
        val reminder = nextReminder(emptyList(), usual, today.atTime(22, 0))!!
        assertEquals(BlockDay(TimeBand.Late, today), reminder.block)
        assertEquals(today.plusDays(1).atTime(0, 45), reminder.at)
        // Logged at 00:10, it counts for that evening.
        assertEquals(today.plusDays(1), nextReminder(listOf(meal(today.plusDays(1).atTime(0, 10))), usual, today.atTime(22, 0))!!.block.day)
    }

    @Test
    fun `no usual time, no reminder`() {
        assertNull(nextReminder(emptyList(), emptyMap(), today.atTime(9, 0)))
    }

    @Test
    fun `a reminder is dropped once its block is logged or it is long overdue`() {
        val block = BlockDay(TimeBand.Lunch, today)
        val at = today.atTime(13, 30)
        assertTrue(reminderStillDue(block, at, emptyList(), at))
        assertFalse(reminderStillDue(block, at, listOf(meal(today.atTime(13, 0))), at))
        assertFalse(reminderStillDue(block, at, emptyList(), at.plusHours(3)))
    }

    @Test
    fun `a late-night meal is one eaten after midnight and logged as it happened`() {
        val night = today.atTime(0, 40)
        assertTrue(isLateNightMeal(night, night.plusMinutes(5)))
        assertFalse(isLateNightMeal(night, night.plusHours(9)))
        assertFalse(isLateNightMeal(today.atTime(23, 30), today.atTime(23, 35)))
        assertFalse(isLateNightMeal(today.atTime(5, 0), today.atTime(5, 1)))
    }

    @Test
    fun `times read on the 24-hour clock`() {
        assertEquals("12:30", formatClock(LocalTime.of(12, 30)))
        assertEquals("00:05", formatClock(LocalTime.of(0, 5)))
    }

    private var nextId = 1L

    private fun meal(eatenAt: LocalDateTime) = Meal(id = nextId++, eatenAt = eatenAt, name = "Meal", price = 100)
}
