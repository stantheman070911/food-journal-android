package com.stanley.foodjournal.logic

import com.stanley.foodjournal.data.Meal
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Usual times are read from at most this many days before today. */
const val USUAL_WINDOW_DAYS = 28L

/** With fewer days of history than this, no block has a usual time yet. */
const val MIN_HISTORY_DAYS = 7L

/** A reminder comes this long after the usual time, if nothing has been logged in its block. */
val REMINDER_DELAY: Duration = Duration.ofHours(1)

/** A reminder that fires this long after its time, as after the phone slept through it, is dropped. */
val REMINDER_STALE_AFTER: Duration = Duration.ofHours(2)

/**
 * One of the day's blocks on one day. A late block runs past midnight, so its [day] is the day it
 * began: a 00:30 snack belongs to the evening before.
 */
data class BlockDay(val band: TimeBand, val day: LocalDate)

/** The block that a meal eaten at [at] falls in. */
fun blockDayOf(at: LocalDateTime): BlockDay {
    val band = timeBand(at.toLocalTime())
    val day = if (band == TimeBand.Late && at.hour < LATE_ENDS_HOUR) at.toLocalDate().minusDays(1) else at.toLocalDate()
    return BlockDay(band, day)
}

/**
 * The usual time of each block eaten in on at least half the days: the median of each day's first
 * entry in that block. The days are the [USUAL_WINDOW_DAYS] before [today], or those since the
 * first entry when that is sooner; today, still under way, never counts. Nothing until there are
 * [MIN_HISTORY_DAYS] days to go on.
 */
fun usualTimes(meals: List<Meal>, today: LocalDate): Map<TimeBand, LocalTime> {
    val first = meals.minOfOrNull { blockDayOf(it.eatenAt).day } ?: return emptyMap()
    val start = maxOf(today.minusDays(USUAL_WINDOW_DAYS), first)
    val days = ChronoUnit.DAYS.between(start, today)
    if (days < MIN_HISTORY_DAYS) return emptyMap()
    val firsts = meals
        .groupBy { blockDayOf(it.eatenAt) }
        .filterKeys { it.day >= start && it.day < today }
        .mapValues { (_, inBlock) -> inBlock.minOf { it.eatenAt } }
    return buildMap {
        for (band in TimeBand.entries) {
            val minutes = firsts.filterKeys { it.band == band }.values.map(::minutesIntoDay).sorted()
            if (minutes.isEmpty() || minutes.size * 2 < days) continue
            put(band, timeOf(median(minutes)))
        }
    }
}

/** A reminder due [at], for [block], whose usual time is [usual]. */
data class Reminder(val block: BlockDay, val at: LocalDateTime, val usual: LocalTime)

/**
 * The next reminder after [now]: the soonest block, from yesterday's late one on, whose usual time
 * plus [REMINDER_DELAY] is still to come and that has nothing logged in it. Null when no block
 * has a usual time.
 */
fun nextReminder(meals: List<Meal>, usual: Map<TimeBand, LocalTime>, now: LocalDateTime): Reminder? {
    if (usual.isEmpty()) return null
    val logged = meals.mapTo(HashSet()) { blockDayOf(it.eatenAt) }
    val today = now.toLocalDate()
    return (-1L..1L)
        .flatMap { offset ->
            usual.map { (band, time) ->
                val block = BlockDay(band, today.plusDays(offset))
                Reminder(block, reminderTime(block, time), time)
            }
        }
        .filter { it.at > now && it.block !in logged }
        .minByOrNull { it.at }
}

/** When [block]'s reminder is due, for a block usually eaten at [usual]. */
fun reminderTime(block: BlockDay, usual: LocalTime): LocalDateTime {
    val eaten = block.day.atTime(usual)
    // A late block's small hours belong to the next calendar day.
    val onDay = if (block.band == TimeBand.Late && usual.hour < LATE_ENDS_HOUR) eaten.plusDays(1) else eaten
    return onDay.plus(REMINDER_DELAY)
}

/**
 * Whether a reminder for [block] due [at] should still show at [now]: nothing has been logged
 * in its block, and it isn't long overdue.
 */
fun reminderStillDue(block: BlockDay, at: LocalDateTime, meals: List<Meal>, now: LocalDateTime): Boolean =
    now <= at.plus(REMINDER_STALE_AFTER) && meals.none { blockDayOf(it.eatenAt) == block }

/**
 * Whether a new meal saved at [savedAt] and eaten at [eatenAt] is a late-night one for the note:
 * eaten between 00:00 and 04:59, and logged as it happened, within an hour, rather than added later.
 */
fun isLateNightMeal(eatenAt: LocalDateTime, savedAt: LocalDateTime): Boolean =
    eatenAt.hour < LATE_ENDS_HOUR && Duration.between(eatenAt, savedAt).abs() <= LATE_NIGHT_WINDOW

/** A time on the 24-hour clock, as the app writes times: "12:30", "00:05". */
fun formatClock(time: LocalTime): String = "%02d:%02d".format(Locale.ROOT, time.hour, time.minute)

private const val LATE_ENDS_HOUR = 5
private const val MINUTES_A_DAY = 24 * 60
private val LATE_NIGHT_WINDOW = Duration.ofHours(1)

/** Minutes since midnight, counting the small hours as after the evening, so a late block sorts in order. */
private fun minutesIntoDay(at: LocalDateTime): Int {
    val minutes = at.hour * 60 + at.minute
    return if (at.hour < LATE_ENDS_HOUR) minutes + MINUTES_A_DAY else minutes
}

/** The middle value, or the mean of the two middle ones, to the minute. */
private fun median(sorted: List<Int>): Int {
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
}

private fun timeOf(minutes: Int): LocalTime = LocalTime.of((minutes % MINUTES_A_DAY) / 60, minutes % 60)
