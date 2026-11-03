package com.stanley.foodjournal.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupTest {
    private val today = LocalDate.of(2026, 11, 4)

    @Test
    fun `a backup over 30 days old brings the reminder`() {
        val firstMeal = LocalDate.of(2026, 1, 1)
        assertFalse(needsBackupReminder(lastBackup = today.minusDays(30), firstMeal = firstMeal, today = today))
        assertTrue(needsBackupReminder(lastBackup = today.minusDays(31), firstMeal = firstMeal, today = today))
        assertFalse(needsBackupReminder(lastBackup = today, firstMeal = firstMeal, today = today))
    }

    @Test
    fun `with no backup yet, the first meal counts`() {
        assertFalse(needsBackupReminder(lastBackup = null, firstMeal = today.minusDays(30), today = today))
        assertTrue(needsBackupReminder(lastBackup = null, firstMeal = today.minusDays(31), today = today))
    }

    @Test
    fun `without meals there is nothing to back up`() {
        assertFalse(needsBackupReminder(lastBackup = null, firstMeal = null, today = today))
        assertFalse(needsBackupReminder(lastBackup = today.minusYears(1), firstMeal = null, today = today))
    }

    @Test
    fun `days show their year only outside this one, except in Settings`() {
        assertEquals("Oct 2", formatDay(LocalDate.of(2026, 10, 2), today))
        assertEquals("Dec 30, 2025", formatDay(LocalDate.of(2025, 12, 30), today))
        assertEquals("Oct 2, 2026", formatFullDay(LocalDate.of(2026, 10, 2)))
    }
}
