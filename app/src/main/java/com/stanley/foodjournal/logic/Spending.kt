package com.stanley.foodjournal.logic

import com.stanley.foodjournal.data.Meal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class PeriodKind { Week, Month, Year }

/** One week (Monday to Sunday), calendar month, or calendar year, from [start] to [end]. */
data class Period(val kind: PeriodKind, val start: LocalDate) {
    val end: LocalDate
        get() = when (kind) {
            PeriodKind.Week -> start.plusDays(6)
            PeriodKind.Month -> start.plusMonths(1).minusDays(1)
            PeriodKind.Year -> start.plusYears(1).minusDays(1)
        }

    val days: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1

    /** The period [count] periods later, or earlier for a negative [count]. */
    fun plus(count: Long): Period = Period(
        kind,
        when (kind) {
            PeriodKind.Week -> start.plusWeeks(count)
            PeriodKind.Month -> start.plusMonths(count)
            PeriodKind.Year -> start.plusYears(count)
        },
    )

    operator fun contains(date: LocalDate): Boolean = date in start..end
}

/** The period of [kind] that [date] falls in. */
fun periodOf(kind: PeriodKind, date: LocalDate): Period = when (kind) {
    PeriodKind.Week -> Period(kind, weekStart(date))
    PeriodKind.Month -> Period(kind, date.withDayOfMonth(1))
    PeriodKind.Year -> Period(kind, date.withDayOfYear(1))
}

/**
 * The periods Spending can swipe through: from the one holding [earliest] to the one holding
 * [today], oldest first. Never past today's period, even for a meal dated later.
 */
fun periodsThrough(kind: PeriodKind, earliest: LocalDate, today: LocalDate): List<Period> {
    val last = periodOf(kind, today)
    var period = minOf(periodOf(kind, earliest), last, compareBy { it.start })
    return buildList {
        while (period.start <= last.start) {
            add(period)
            period = period.plus(1)
        }
    }
}

/** Days of [period] up to and including [today]: all of them once it is over, at least one. */
fun elapsedDays(period: Period, today: LocalDate): Int = when {
    today >= period.end -> period.days
    today < period.start -> 1
    else -> ChronoUnit.DAYS.between(period.start, today).toInt() + 1
}

/** The four parts of the day. Late runs past midnight, from 22:00 to 04:59. */
enum class TimeBand { Morning, Midday, Evening, Late }

fun timeBand(time: LocalTime): TimeBand = when (time.hour) {
    in 5..10 -> TimeBand.Morning
    in 11..15 -> TimeBand.Midday
    in 16..21 -> TimeBand.Evening
    else -> TimeBand.Late
}

/**
 * One bar: a day of a week or month, or a month of a year. A future bar has no amount, so a meal
 * dated later never sets the chart's scale.
 */
data class SpendingBar(val start: LocalDate, val amount: Int, val isFuture: Boolean)

/** A place's spend in a period. [visits] counts its meals. [lastVisit] breaks ties. */
data class PlaceSpend(val placeId: Long, val total: Int, val visits: Int, val lastVisit: LocalDateTime)

/** The places past the top five, and meals without a place. */
data class OtherSpend(val total: Int, val meals: Int)

/** Everything the Spending tab shows for one period, computed from its meals. */
data class PeriodSpending(
    val period: Period,
    val total: Int,
    /** The signed change from the previous period in whole percent, or null to leave it out. */
    val change: Int?,
    val dailyAverage: Int,
    val perMeal: Int,
    val bars: List<SpendingBar>,
    val topPlaces: List<PlaceSpend>,
    val other: OtherSpend?,
    /** All four bands, in order, with their totals. */
    val bands: List<Pair<TimeBand, Int>>,
    val priciest: List<Meal>,
)

private const val TOP_PLACES = 5
private const val PRICIEST = 3

/**
 * Spending for [period] from [meals], the meals dated inside it, and [previousMeals], those of
 * the period before. Returns null when the period has no meals.
 *
 * Only places in [knownPlaces] get a row of their own; a meal pointing anywhere else counts
 * toward Other, as a meal without a place does.
 */
fun periodSpending(
    period: Period,
    meals: List<Meal>,
    previousMeals: List<Meal>,
    today: LocalDate,
    knownPlaces: Set<Long>,
): PeriodSpending? {
    if (meals.isEmpty()) return null
    val total = meals.sumOf { it.price }

    val placed = meals
        .filter { it.placeId != null && it.placeId in knownPlaces }
        .groupBy { it.placeId!! }
        .map { (placeId, atPlace) ->
            PlaceSpend(placeId, atPlace.sumOf { it.price }, atPlace.size, atPlace.maxOf { it.eatenAt })
        }
        .sortedWith(
            compareByDescending<PlaceSpend> { it.total }.thenByDescending { it.lastVisit }.thenByDescending { it.placeId },
        )
    val top = placed.take(TOP_PLACES)
    val otherMeals = meals.size - top.sumOf { it.visits }
    val other = if (otherMeals > 0) OtherSpend(total - top.sumOf { it.total }, otherMeals) else null

    val byBand = meals.groupBy { timeBand(it.eatenAt.toLocalTime()) }

    return PeriodSpending(
        period = period,
        total = total,
        change = change(period, total, previousMeals, today),
        dailyAverage = roundedDivision(total, elapsedDays(period, today)),
        perMeal = roundedDivision(total, meals.size),
        bars = bars(period, meals, today),
        topPlaces = top,
        other = other,
        bands = TimeBand.entries.map { band -> band to byBand[band].orEmpty().sumOf { it.price } },
        priciest = meals
            .filter { it.price > 0 }
            .sortedWith(compareByDescending<Meal> { it.price }.thenByDescending { it.eatenAt }.thenByDescending { it.id })
            .take(PRICIEST),
    )
}

/**
 * The change from the period before, in whole percent. A finished period, one that ended before
 * [today], is compared with all of the previous one. An unfinished period, its last day included,
 * is compared with the same number of days from the start of the previous one, or all of it when
 * that is shorter. Null when the compared days have no meals or cost nothing.
 */
private fun change(period: Period, total: Int, previousMeals: List<Meal>, today: LocalDate): Int? {
    val previous = period.plus(-1)
    val compared = if (today > period.end) previous.days else minOf(elapsedDays(period, today), previous.days)
    val cutoff = previous.start.plusDays(compared.toLong())
    val before = previousMeals.filter { it.eatenAt.toLocalDate() < cutoff }.sumOf { it.price }
    return if (before == 0) null else percentChange(total, before)
}

/**
 * The change from [before] to [now] in whole percent, halves rounded away from zero. A change too
 * large for an Int, from a tiny previous total, is held at the largest one.
 */
fun percentChange(now: Int, before: Int): Int {
    val scaled = (now.toLong() - before) * 100
    val magnitude = (Math.abs(scaled) * 2 + before) / (2L * before)
    return (if (scaled < 0) -magnitude else magnitude).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
}

/** [total] ÷ [count] to the nearest whole dollar, halves up. */
private fun roundedDivision(total: Int, count: Int): Int = ((total.toLong() * 2 + count) / (2L * count)).toInt()

private fun bars(period: Period, meals: List<Meal>, today: LocalDate): List<SpendingBar> =
    if (period.kind == PeriodKind.Year) {
        val byMonth = meals.groupBy { it.eatenAt.monthValue }
        (1..12).map { month ->
            val start = period.start.withMonth(month)
            val isFuture = start > today
            SpendingBar(start, if (isFuture) 0 else byMonth[month].orEmpty().sumOf { it.price }, isFuture)
        }
    } else {
        val byDay = meals.groupBy { it.eatenAt.toLocalDate() }
        (0 until period.days).map { offset ->
            val day = period.start.plusDays(offset.toLong())
            val isFuture = day > today
            SpendingBar(day, if (isFuture) 0 else byDay[day].orEmpty().sumOf { it.price }, isFuture)
        }
    }

private val MonthName = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)
private val MonthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val ShortMonth = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
private val ShortMonthDay = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
private val NarrowWeekday = DateTimeFormatter.ofPattern("EEEEE", Locale.ENGLISH)
private val NarrowMonth = DateTimeFormatter.ofPattern("MMMMM", Locale.ENGLISH)

/**
 * A period's name above its total: "Nov 2 – 8" as in the Journal, "November" ("November 2025"
 * outside [today]'s year), or "2026".
 */
fun formatPeriodName(period: Period, today: LocalDate): String = when (period.kind) {
    PeriodKind.Week -> formatWeekRange(period.start, today)
    PeriodKind.Month -> period.start.format(if (period.start.year == today.year) MonthName else MonthYear)
    PeriodKind.Year -> period.start.year.toString()
}

/** What the change line compares with: "vs last week", "vs previous week", or "vs Oct". */
sealed interface ComparedWith {
    data object LastWeek : ComparedWith
    data object PreviousWeek : ComparedWith
    data class Named(val name: String) : ComparedWith
}

fun comparedWith(period: Period, today: LocalDate): ComparedWith {
    val previous = period.plus(-1)
    return when (period.kind) {
        PeriodKind.Week -> if (today in period) ComparedWith.LastWeek else ComparedWith.PreviousWeek
        PeriodKind.Month -> ComparedWith.Named(previous.start.format(ShortMonth))
        PeriodKind.Year -> ComparedWith.Named(previous.start.year.toString())
    }
}

/** A change as the line shows it, with a true minus: "−12%", "+4%", "0%". */
fun formatChange(percent: Int): String = when {
    percent > 0 -> "+${formatPrice(percent)}%"
    percent < 0 -> "−${formatPrice(-percent)}%"
    else -> "0%"
}

/**
 * A bar's label under the chart: "M" to "S" for a week, every seventh day from the 1st for a
 * month, and the month's initial for a year. Null where a month's day goes unlabeled.
 */
fun axisLabel(kind: PeriodKind, start: LocalDate): String? = when (kind) {
    PeriodKind.Week -> start.format(NarrowWeekday)
    PeriodKind.Month -> start.dayOfMonth.takeIf { it % 7 == 1 }?.toString()
    PeriodKind.Year -> start.format(NarrowMonth)
}

/** What a bar reads aloud: "Nov 12, 340", or "November, 2,340" for a month of a year. */
fun describeBar(kind: PeriodKind, bar: SpendingBar): String {
    val date = bar.start.format(if (kind == PeriodKind.Year) MonthName else ShortMonthDay)
    return "$date, ${formatPrice(bar.amount)}"
}
