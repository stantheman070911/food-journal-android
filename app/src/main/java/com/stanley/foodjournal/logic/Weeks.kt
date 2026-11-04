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

/** The Journal's strips run in this order. Meals saved at the same moment keep their saved order. */
private val OrderEaten = compareBy<Meal> { it.eatenAt }.thenBy { it.id }

/**
 * Where a meal stands among all meals: the meal before and after it in the order eaten, across
 * weeks, and its place among the [weekCount] meals of its own week.
 */
data class MealSteps(
    val previous: Long?,
    val next: Long?,
    val weekCount: Int,
    val weekIndex: Int,
)

/** The steps from the meal with [mealId], in the Journal's order, or null when it isn't in [meals]. */
fun mealSteps(meals: List<Meal>, mealId: Long): MealSteps? {
    val ordered = meals.sortedWith(OrderEaten)
    val index = ordered.indexOfFirst { it.id == mealId }
    if (index < 0) return null
    val week = weekStart(ordered[index].eatenAt.toLocalDate())
    val inWeek = ordered.filter { weekStart(it.eatenAt.toLocalDate()) == week }
    return MealSteps(
        previous = ordered.getOrNull(index - 1)?.id,
        next = ordered.getOrNull(index + 1)?.id,
        weekCount = inWeek.size,
        weekIndex = inWeek.indexOfFirst { it.id == mealId },
    )
}

/**
 * Meals grouped into weeks, newest week first, and within a week from Monday to Sunday. A
 * meal's day is the calendar date it was eaten, so a 01:00 snack counts toward that day.
 * Weeks without meals are left out.
 */
fun groupIntoWeeks(meals: List<Meal>): List<Week> =
    meals
        .sortedWith(OrderEaten)
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
fun formatWeekRange(start: LocalDate, today: LocalDate): String = formatDateRange(start, start.plusDays(6), today)

/**
 * Any run of days, written as a week's header is: "Oct 1 – Nov 4", "Nov 2 – 8", and a single
 * day as "Nov 4". The year shows only for dates outside [today]'s year.
 */
fun formatDateRange(start: LocalDate, end: LocalDate, today: LocalDate): String {
    val thisYear = today.year
    if (start == end) {
        val year = if (start.year != thisYear) ", ${start.format(Year)}" else ""
        return "${start.format(MonthDay)}$year"
    }
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
