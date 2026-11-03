package com.stanley.foodjournal.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun `stores fixed-width text to the second`() {
        assertEquals(
            "2026-11-03T12:30:00",
            converters.fromLocalDateTime(LocalDateTime.of(2026, 11, 3, 12, 30)),
        )
        assertEquals(
            "2026-01-05T01:02:03",
            converters.fromLocalDateTime(LocalDateTime.of(2026, 1, 5, 1, 2, 3, 999_000_000)),
        )
    }

    @Test
    fun `round trips to the second`() {
        val eatenAt = LocalDateTime.of(2026, 11, 3, 23, 59, 59)
        assertEquals(eatenAt, converters.toLocalDateTime(converters.fromLocalDateTime(eatenAt)))
    }

    @Test
    fun `text order matches time order`() {
        val times = listOf(
            LocalDateTime.of(2025, 12, 31, 23, 59, 59),
            LocalDateTime.of(2026, 1, 1, 0, 0, 0),
            LocalDateTime.of(2026, 1, 1, 0, 0, 1),
            LocalDateTime.of(2026, 1, 1, 9, 5, 0),
            LocalDateTime.of(2026, 1, 1, 10, 0, 0),
            LocalDateTime.of(2026, 10, 1, 0, 0, 0),
        )
        val stored = times.map(converters::fromLocalDateTime)
        assertEquals(stored, stored.sorted())
    }
}
