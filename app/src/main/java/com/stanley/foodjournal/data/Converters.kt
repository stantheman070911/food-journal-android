package com.stanley.foodjournal.data

import androidx.room.TypeConverter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Stores [LocalDateTime] as fixed-width ISO-8601 text to the second, such as
 * "2026-11-03T12:30:00". Fixed width keeps text order equal to time order, so range queries and
 * ORDER BY work on the column directly.
 */
class Converters {
    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime): String =
        value.truncatedTo(ChronoUnit.SECONDS).format(FORMAT)

    @TypeConverter
    fun toLocalDateTime(value: String): LocalDateTime = LocalDateTime.parse(value, FORMAT)

    private companion object {
        val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss")
    }
}
