package com.stanley.foodjournal.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class MealFormTest {
    @Test
    fun `price input keeps digits only, up to six`() {
        assertEquals("280", cleanPriceInput("280"))
        assertEquals("1200", cleanPriceInput("1,200"))
        assertEquals("999999", cleanPriceInput("9999999"))
        assertEquals("", cleanPriceInput("abc"))
    }

    @Test
    fun `price input drops leading zeros but keeps a free meal`() {
        assertEquals("7", cleanPriceInput("007"))
        assertEquals("0", cleanPriceInput("0"))
        assertEquals("0", cleanPriceInput("000"))
        assertEquals("5", cleanPriceInput("05"))
    }

    @Test
    fun `save needs a photo or a name, and a price`() {
        assertTrue(canSave(hasPhoto = true, name = "", priceText = "280"))
        assertTrue(canSave(hasPhoto = false, name = "Dumplings", priceText = "0"))
        assertFalse(canSave(hasPhoto = false, name = "  ", priceText = "280"))
        assertFalse(canSave(hasPhoto = true, name = "Dumplings", priceText = ""))
    }

    @Test
    fun `prices group digits in English`() {
        assertEquals("280", formatPrice(280))
        assertEquals("2,340", formatPrice(2_340))
        assertEquals("999,999", formatPrice(999_999))
        assertEquals("0", formatPrice(0))
    }

    @Test
    fun `typed prices group as they are typed`() {
        assertEquals("", groupDigits(""))
        assertEquals("2,340", groupDigits("2340"))
        assertEquals("123,456", groupDigits("123456"))
    }

    @Test
    fun `cursor positions survive grouping both ways`() {
        val digits = "2340"
        val expected = listOf(0, 1, 3, 4, 5)
        assertEquals(expected, (0..4).map { originalToGrouped(digits, it) })
        assertEquals((0..4).toList(), expected.map { groupedToOriginal(digits, it) })
        assertEquals(listOf(0, 1, 2, 3), (0..3).map { originalToGrouped("280", it) })
    }

    @Test
    fun `suggests recent names that start with, then contain, the typed text`() {
        val recent = listOf("Beef noodle soup", "Braised pork rice", "Noodles", "beef")
        assertEquals(listOf("Beef noodle soup"), suggestNames(recent, "beef"))
        assertEquals(listOf("Noodles", "Beef noodle soup"), suggestNames(recent, "noodle"))
        assertEquals(emptyList<String>(), suggestNames(recent, " "))
    }

    @Test
    fun `formats meal times with the date and weekday`() {
        val today = LocalDate.of(2026, 11, 3)
        assertEquals("Tue, Nov 3 · 12:40", formatMealTime(LocalDateTime.of(2026, 11, 3, 12, 40), today))
        assertEquals("Mon, Nov 2 · 01:05", formatMealTime(LocalDateTime.of(2026, 11, 2, 1, 5), today))
        assertEquals("Tue, Oct 27 · 19:05", formatMealTime(LocalDateTime.of(2026, 10, 27, 19, 5), today))
        assertEquals("Wed, Dec 31, 2025 · 23:30", formatMealTime(LocalDateTime.of(2025, 12, 31, 23, 30), today))
    }

    @Test
    fun `reads EXIF wall-clock dates`() {
        assertEquals(LocalDateTime.of(2026, 10, 27, 12, 40, 5), parseExifDateTime("2026:10:27 12:40:05"))
        assertEquals(LocalDateTime.of(2026, 10, 27, 12, 40, 5), parseExifDateTime("2026:10:27 12:40:05\u0000"))
    }

    @Test
    fun `treats missing or blank EXIF dates as none`() {
        assertNull(parseExifDateTime(null))
        assertNull(parseExifDateTime(""))
        assertNull(parseExifDateTime("    :  :     :  :  "))
        assertNull(parseExifDateTime("0000:00:00 00:00:00"))
    }
}
