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

class SpendingTest {
    // A Wednesday: the current week is Nov 2 – 8.
    private val today = LocalDate.of(2026, 11, 4)

    private val november = Period(PeriodKind.Month, LocalDate.of(2026, 11, 1))
    private val october = Period(PeriodKind.Month, LocalDate.of(2026, 10, 1))

    @Test
    fun `periods hold the date they are made from`() {
        assertEquals(Period(PeriodKind.Week, LocalDate.of(2026, 11, 2)), periodOf(PeriodKind.Week, today))
        assertEquals(november, periodOf(PeriodKind.Month, today))
        assertEquals(Period(PeriodKind.Year, LocalDate.of(2026, 1, 1)), periodOf(PeriodKind.Year, today))
        assertEquals(LocalDate.of(2026, 11, 8), periodOf(PeriodKind.Week, today).end)
        assertEquals(LocalDate.of(2026, 11, 30), november.end)
        assertEquals(LocalDate.of(2026, 12, 31), periodOf(PeriodKind.Year, today).end)
        assertEquals(28, Period(PeriodKind.Month, LocalDate.of(2027, 2, 1)).days)
        assertEquals(366, Period(PeriodKind.Year, LocalDate.of(2028, 1, 1)).days)
    }

    @Test
    fun `swiping runs from the earliest meal's period to today's`() {
        val months = periodsThrough(PeriodKind.Month, LocalDate.of(2026, 9, 30), today)
        assertEquals(listOf(9, 10, 11), months.map { it.start.monthValue })

        val weeks = periodsThrough(PeriodKind.Week, LocalDate.of(2026, 10, 26), today)
        assertEquals(listOf(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 2)), weeks.map { it.start })
    }

    @Test
    fun `a meal dated later never takes swiping past today's period`() {
        val months = periodsThrough(PeriodKind.Month, LocalDate.of(2027, 2, 1), today)
        assertEquals(listOf(november), months)
    }

    @Test
    fun `elapsed days end at today, and cover a finished period`() {
        assertEquals(4, elapsedDays(november, today))
        assertEquals(31, elapsedDays(october, today))
        assertEquals(3, elapsedDays(periodOf(PeriodKind.Week, today), today))
        assertEquals(308, elapsedDays(periodOf(PeriodKind.Year, today), today))
    }

    @Test
    fun `time bands split the day, with late running past midnight`() {
        assertEquals(TimeBand.Late, timeBand(LocalTime.of(4, 59)))
        assertEquals(TimeBand.Morning, timeBand(LocalTime.of(5, 0)))
        assertEquals(TimeBand.Morning, timeBand(LocalTime.of(10, 59)))
        assertEquals(TimeBand.Midday, timeBand(LocalTime.of(11, 0)))
        assertEquals(TimeBand.Midday, timeBand(LocalTime.of(15, 59)))
        assertEquals(TimeBand.Evening, timeBand(LocalTime.of(16, 0)))
        assertEquals(TimeBand.Evening, timeBand(LocalTime.of(21, 59)))
        assertEquals(TimeBand.Late, timeBand(LocalTime.of(22, 0)))
        assertEquals(TimeBand.Late, timeBand(LocalTime.of(0, 30)))
    }

    @Test
    fun `a period with no meals has nothing to show`() {
        assertNull(periodSpending(november, emptyList(), listOf(meal(1, day(10, 1))), today, emptySet()))
    }

    @Test
    fun `total and averages, with free meals counting as meals`() {
        val spending = spend(
            november,
            listOf(meal(1, day(11, 1), 500), meal(2, day(11, 2), 0), meal(3, day(11, 4), 750)),
        )

        assertEquals(1_250, spending.total)
        // Four days have passed: 1,250 ÷ 4 = 312.5, rounded up.
        assertEquals(313, spending.dailyAverage)
        // Three meals: 1,250 ÷ 3 = 416.7.
        assertEquals(417, spending.perMeal)
    }

    @Test
    fun `a finished period's daily average divides by all its days`() {
        val spending = spend(october, listOf(meal(1, day(10, 5), 3_100)))
        assertEquals(100, spending.dailyAverage)
    }

    @Test
    fun `an unfinished period compares the same days of the previous one`() {
        val previous = listOf(meal(1, day(10, 2), 400), meal(2, day(10, 4), 100), meal(3, day(10, 5), 900))

        // Nov 1 – 4 against Oct 1 – 4: 450 against 500.
        val spending = spend(november, listOf(meal(4, day(11, 3), 450)), previous)

        assertEquals(-10, spending.change)
    }

    @Test
    fun `a finished period compares all of the previous one, even a longer one`() {
        val february = Period(PeriodKind.Month, LocalDate.of(2026, 2, 1))
        val january = listOf(meal(1, LocalDateTime.of(2026, 1, 3, 12, 0), 100), meal(2, LocalDateTime.of(2026, 1, 30, 12, 0), 100))

        val spending = spend(february, listOf(meal(3, LocalDateTime.of(2026, 2, 10, 12, 0), 300)), january)

        assertEquals(50, spending.change)
    }

    @Test
    fun `an unfinished period longer than the previous one compares up to its last day`() {
        val march = Period(PeriodKind.Month, LocalDate.of(2027, 3, 1))
        val february = listOf(meal(1, LocalDateTime.of(2027, 2, 28, 20, 0), 200))

        val spending = periodSpending(
            march,
            listOf(meal(2, LocalDateTime.of(2027, 3, 30, 12, 0), 300)),
            february,
            today = LocalDate.of(2027, 3, 30),
            knownPlaces = emptySet(),
        )!!

        assertEquals(50, spending.change)
    }

    @Test
    fun `a year compares days counted from Jan 1, so Feb 29 shifts nothing`() {
        val year = Period(PeriodKind.Year, LocalDate.of(2029, 1, 1))
        // In 2028, day 60 is Feb 29, so Mar 1 is day 61 and falls outside.
        val previous = listOf(
            meal(1, LocalDateTime.of(2028, 2, 29, 12, 0), 100),
            meal(2, LocalDateTime.of(2028, 3, 1, 12, 0), 900),
        )

        val spending = periodSpending(
            year,
            listOf(meal(3, LocalDateTime.of(2029, 2, 10, 12, 0), 150)),
            previous,
            today = LocalDate.of(2029, 3, 1),
            knownPlaces = emptySet(),
        )!!

        assertEquals(50, spending.change)
    }

    @Test
    fun `the change is left out when the compared days have no meals or cost nothing`() {
        val now = listOf(meal(1, day(11, 2), 300))

        assertNull(spend(november, now).change)
        assertNull(spend(november, now, listOf(meal(2, day(10, 2), 0))).change)
        // October has meals, but none in its first four days.
        assertNull(spend(november, now, listOf(meal(3, day(10, 20), 500))).change)
    }

    @Test
    fun `percent changes round to the nearest whole percent, halves away from zero`() {
        assertEquals(-12, percentChange(880, 1_000))
        assertEquals(4, percentChange(1_040, 1_000))
        assertEquals(0, percentChange(1_004, 1_000))
        assertEquals(0, percentChange(996, 1_000))
        assertEquals(13, percentChange(1_125, 1_000))
        assertEquals(-13, percentChange(875, 1_000))
        assertEquals(-100, percentChange(0, 1_000))
        assertEquals(1_250, percentChange(1_350, 100))
    }

    @Test
    fun `changes show a sign, with a true minus`() {
        assertEquals("−12%", formatChange(-12))
        assertEquals("+4%", formatChange(4))
        assertEquals("0%", formatChange(0))
        assertEquals("+1,250%", formatChange(1_250))
    }

    @Test
    fun `a month has a bar per day, and days after today are in the future`() {
        val spending = spend(november, listOf(meal(1, day(11, 2, 1), 80), meal(2, day(11, 2, 13), 120)))

        assertEquals(30, spending.bars.size)
        assertEquals(200, spending.bars[1].amount)
        assertEquals(0, spending.bars[0].amount)
        assertFalse(spending.bars[3].isFuture)
        assertTrue(spending.bars[4].isFuture)
    }

    @Test
    fun `a week has seven bars and a year twelve`() {
        val week = spend(periodOf(PeriodKind.Week, today), listOf(meal(1, day(11, 8), 100)))
        assertEquals(7, week.bars.size)
        assertEquals(100, week.bars[6].amount)

        val year = spend(periodOf(PeriodKind.Year, today), listOf(meal(1, day(10, 3), 300), meal(2, day(10, 30), 200)))
        assertEquals(12, year.bars.size)
        assertEquals(500, year.bars[9].amount)
        assertFalse(year.bars[10].isFuture)
        assertTrue(year.bars[11].isFuture)
    }

    @Test
    fun `a meal later today counts toward the total`() {
        val spending = spend(november, listOf(meal(1, day(11, 4, 23), 300), meal(2, day(11, 20), 100)))
        assertEquals(400, spending.total)
        // The average still divides by the four days so far.
        assertEquals(100, spending.dailyAverage)
    }

    @Test
    fun `top places rank by spend, ties going to the most recent visit`() {
        val meals = listOf(
            meal(1, day(11, 1), 300, place = 1),
            meal(2, day(11, 2), 300, place = 2),
            meal(3, day(11, 3), 100, place = 3),
            meal(4, day(11, 3), 250, place = 3),
        )

        val places = spend(november, meals, places = setOf(1, 2, 3)).topPlaces

        assertEquals(listOf(3L, 2L, 1L), places.map { it.placeId })
        assertEquals(350, places[0].total)
        assertEquals(2, places[0].visits)
    }

    @Test
    fun `places past the top five, and meals without a place, are Other`() {
        val meals = (1L..7L).map { meal(it, day(11, 1), price = 100 * it.toInt(), place = it) } +
            meal(8, day(11, 2), 50)

        val spending = spend(november, meals, places = (1L..7L).toSet())

        assertEquals(listOf(7L, 6L, 5L, 4L, 3L), spending.topPlaces.map { it.placeId })
        // Places 1 and 2, and the meal without a place.
        assertEquals(OtherSpend(total = 350, meals = 3), spending.other)
    }

    @Test
    fun `a meal at an unknown place counts toward Other`() {
        val spending = spend(november, listOf(meal(1, day(11, 1), 100, place = 9)), places = emptySet())

        assertTrue(spending.topPlaces.isEmpty())
        assertEquals(OtherSpend(100, 1), spending.other)
    }

    @Test
    fun `Other is left out when every meal has a top place`() {
        assertNull(spend(november, listOf(meal(1, day(11, 1), 100, place = 1)), places = setOf(1)).other)
    }

    @Test
    fun `Other with only free meals still shows`() {
        assertEquals(OtherSpend(0, 1), spend(november, listOf(meal(1, day(11, 1), 0))).other)
    }

    @Test
    fun `every time band shows, in order, even at zero`() {
        val spending = spend(november, listOf(meal(1, day(11, 2, 1), 90), meal(2, day(11, 2, 12), 160)))

        assertEquals(
            listOf(TimeBand.Morning to 0, TimeBand.Midday to 160, TimeBand.Evening to 0, TimeBand.Late to 90),
            spending.bands,
        )
    }

    @Test
    fun `the priciest are the three dearest meals, ties going to the most recent`() {
        val meals = listOf(
            meal(1, day(11, 1), 500),
            meal(2, day(11, 2), 800),
            meal(3, day(11, 3), 500),
            meal(4, day(11, 3), 100),
        )

        assertEquals(listOf(2L, 3L, 1L), spend(november, meals).priciest.map { it.id })
    }

    @Test
    fun `free meals are never the priciest`() {
        val spending = spend(november, listOf(meal(1, day(11, 1), 0), meal(2, day(11, 2), 60)))
        assertEquals(listOf(2L), spending.priciest.map { it.id })
        assertTrue(spend(november, listOf(meal(1, day(11, 1), 0))).priciest.isEmpty())
    }

    @Test
    fun `period names`() {
        assertEquals("Nov 2 – 8", formatPeriodName(periodOf(PeriodKind.Week, today), today))
        assertEquals("November", formatPeriodName(november, today))
        assertEquals("December 2025", formatPeriodName(Period(PeriodKind.Month, LocalDate.of(2025, 12, 1)), today))
        assertEquals("2026", formatPeriodName(periodOf(PeriodKind.Year, today), today))
    }

    @Test
    fun `the change line names what it compares with`() {
        assertEquals(ComparedWith.LastWeek, comparedWith(periodOf(PeriodKind.Week, today), today))
        assertEquals(ComparedWith.PreviousWeek, comparedWith(periodOf(PeriodKind.Week, LocalDate.of(2026, 10, 20)), today))
        assertEquals(ComparedWith.Named("Oct"), comparedWith(november, today))
        assertEquals(ComparedWith.Named("Dec"), comparedWith(Period(PeriodKind.Month, LocalDate.of(2027, 1, 1)), today))
        assertEquals(ComparedWith.Named("2025"), comparedWith(periodOf(PeriodKind.Year, today), today))
    }

    @Test
    fun `axis labels`() {
        assertEquals("M", axisLabel(PeriodKind.Week, LocalDate.of(2026, 11, 2)))
        assertEquals("S", axisLabel(PeriodKind.Week, LocalDate.of(2026, 11, 8)))
        assertEquals(listOf("1", "8", "15", "22", "29"), (1..30).mapNotNull { axisLabel(PeriodKind.Month, LocalDate.of(2026, 11, it)) })
        assertEquals("N", axisLabel(PeriodKind.Year, LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun `bars read aloud as their date and amount`() {
        assertEquals("Nov 12, 340", describeBar(PeriodKind.Month, SpendingBar(LocalDate.of(2026, 11, 12), 340, false)))
        assertEquals("November, 2,340", describeBar(PeriodKind.Year, SpendingBar(LocalDate.of(2026, 11, 1), 2_340, false)))
    }

    private fun spend(
        period: Period,
        meals: List<Meal>,
        previous: List<Meal> = emptyList(),
        places: Set<Long> = emptySet(),
    ) = periodSpending(period, meals, previous, today, places)!!

    private fun day(month: Int, day: Int, hour: Int = 12) = LocalDateTime.of(2026, month, day, hour, 0)

    private fun meal(id: Long, eatenAt: LocalDateTime, price: Int = 100, place: Long? = null) =
        Meal(id = id, eatenAt = eatenAt, name = "Meal $id", price = price, placeId = place)
}
