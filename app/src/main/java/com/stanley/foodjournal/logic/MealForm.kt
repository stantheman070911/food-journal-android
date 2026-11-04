package com.stanley.foodjournal.logic

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Prices are whole NTD, up to 999,999. */
const val PRICE_MAX_DIGITS = 6

/**
 * Cleans what was typed into the price field: digits only, at most [PRICE_MAX_DIGITS], and no
 * leading zeros, so "007" becomes "7". A lone "0" stays, since a free meal is allowed.
 */
fun cleanPriceInput(typed: String): String {
    val digits = typed.filter { it in '0'..'9' }.trimStart('0')
    val cleaned = if (digits.isEmpty() && typed.any { it == '0' }) "0" else digits
    return cleaned.take(PRICE_MAX_DIGITS)
}

/** The price in a cleaned price field, or null while it is empty. */
fun parsePrice(cleaned: String): Int? = cleaned.toIntOrNull()

/** A meal can be saved once it has a photo or a name, and a price. */
fun canSave(hasPhoto: Boolean, name: String, priceText: String): Boolean =
    (hasPhoto || name.isNotBlank()) && parsePrice(priceText) != null

/** A price with English digit grouping and no currency: "280", "2,340". */
fun formatPrice(price: Int): String = "%,d".format(Locale.ENGLISH, price)

/**
 * Groups the digits of a cleaned price field as they are typed: "2340" shows as "2,340".
 * [originalToGrouped] and [groupedToOriginal] map cursor positions between the two.
 */
fun groupDigits(digits: String): String =
    if (digits.isEmpty()) digits else formatPrice(digits.toInt())

fun originalToGrouped(digits: String, offset: Int): Int {
    val commasBefore = (digits.length - 1) / 3 - (digits.length - offset) / 3
    return offset + commasBefore.coerceAtLeast(0)
}

fun groupedToOriginal(digits: String, offset: Int): Int {
    val grouped = groupDigits(digits)
    return offset - grouped.take(offset).count { it == ',' }
}

/** Recent names that start with, then contain, what is typed. Never the typed name itself. */
fun suggestNames(recent: List<String>, typed: String, limit: Int = 3): List<String> {
    val query = typed.trim()
    if (query.isEmpty()) return emptyList()
    val candidates = recent.filterNot { it.equals(query, ignoreCase = true) }
    val starts = candidates.filter { it.startsWith(query, ignoreCase = true) }
    val contains = candidates.filter { it.contains(query, ignoreCase = true) && it !in starts }
    return (starts + contains).take(limit)
}

private val TimeOfDay = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

/**
 * When a meal was eaten, as the Meal screen shows it: "Tue, Nov 3 · 18:03", with the year outside
 * [today]'s year: "Mon, Oct 27, 2025 · 19:05".
 */
fun formatMealTime(eatenAt: LocalDateTime, today: LocalDate): String =
    "${formatResultDate(eatenAt.toLocalDate(), today)} · ${eatenAt.format(TimeOfDay)}"

private val ExifDateTime = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ROOT)

/**
 * The wall-clock time in an EXIF date such as "2026:10:27 12:40:05", or null when the value is
 * missing or malformed. Cameras write blanks or zeros when they have no clock, so those count
 * as missing too.
 */
fun parseExifDateTime(value: String?): LocalDateTime? {
    val text = value?.trim()?.takeIf { it.length >= 19 } ?: return null
    return try {
        LocalDateTime.parse(text.take(19), ExifDateTime)
    } catch (_: DateTimeParseException) {
        null
    }
}
