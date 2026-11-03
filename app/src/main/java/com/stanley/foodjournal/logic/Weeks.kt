package com.stanley.foodjournal.logic

import com.stanley.foodjournal.data.Meal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** One week of meals, Monday to Sunday, in the order they were eaten. */
data class Week(
    val start: LocalDate,
    val meals: List<Meal>,
) {
    val end: LocalDate get() = start.plusDays(6)
    val total: Int get() = meals.sumOf { it.price }
}

/** The Monday of the week [date] falls in. Weeks run Monday to Sunday. */
fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/**
 * Meals grouped into weeks, newest week first, and within a week from Monday to Sunday. A
 * meal's day is the calendar date it was eaten, so a 01:00 snack counts toward that day.
 * Weeks without meals are left out.
 */
fun groupIntoWeeks(meals: List<Meal>): List<Week> =
    meals
        .sortedWith(compareBy<Meal> { it.eatenAt }.thenBy { it.id })
        .groupBy { weekStart(it.eatenAt.toLocalDate()) }
        .map { (start, inWeek) -> Week(start, inWeek) }
        .sortedByDescending { it.start }

private val MonthDay = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
private val Day = DateTimeFormatter.ofPattern("d", Locale.ENGLISH)
private val Year = DateTimeFormatter.ofPattern("yyyy", Locale.ENGLISH)
private val Weekday = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

/**
 * A week's dates as its header shows them: "Oct 26 – Nov 1", or "Nov 2 – 8" within one month.
 * The year shows only for dates outside [today]'s year: "Oct 20 – 26, 2025", and across New
 * Year "Dec 29, 2025 – Jan 4".
 */
fun formatWeekRange(start: LocalDate, today: LocalDate): String {
    val end = start.plusDays(6)
    val thisYear = today.year
    return if (start.year == end.year) {
        val endText = if (start.month == end.month) end.format(Day) else end.format(MonthDay)
        val year = if (start.year != thisYear) ", ${start.format(Year)}" else ""
        "${start.format(MonthDay)} – $endText$year"
    } else {
        val startYear = if (start.year != thisYear) ", ${start.format(Year)}" else ""
        val endYear = if (end.year != thisYear) ", ${end.format(Year)}" else ""
        "${start.format(MonthDay)}$startYear – ${end.format(MonthDay)}$endYear"
    }
}

/** The day a meal was eaten, as its tile labels it: "Mon". */
fun formatWeekday(date: LocalDate): String = date.format(Weekday)
