package com.stanley.foodjournal.logic

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** A backup older than this many days brings the reminder back. */
const val BACKUP_REMINDER_DAYS = 30

/**
 * Whether the Journal shows its backup reminder: when the last backup is over 30 days old, or,
 * with no backup yet, when the first meal is. Without meals there is nothing to back up.
 */
fun needsBackupReminder(lastBackup: LocalDate?, firstMeal: LocalDate?, today: LocalDate): Boolean {
    if (firstMeal == null) return false
    val since = lastBackup ?: firstMeal
    return ChronoUnit.DAYS.between(since, today) > BACKUP_REMINDER_DAYS
}

private val MonthDay = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
private val MonthDayYear = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

/** "Oct 30", with the year only outside the current one: "Oct 30, 2025". */
fun formatDay(date: LocalDate, today: LocalDate): String =
    date.format(if (date.year == today.year) MonthDay else MonthDayYear)

/** "Oct 2, 2026", always with the year, as Settings shows the last backup. */
fun formatFullDay(date: LocalDate): String = date.format(MonthDayYear)
