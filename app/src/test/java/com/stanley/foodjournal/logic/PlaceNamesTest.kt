package com.stanley.foodjournal.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceNamesTest {
    @Test
    fun `trims, collapses spaces, and narrows full-width letters`() {
        assertEquals("Starbucks", normalizePlaceName("  Starbucks "))
        assertEquals("Lin Dong Fang", normalizePlaceName("Lin   Dong\tFang"))
        assertEquals("Starbucks", normalizePlaceName("Ｓｔａｒｂｕｃｋｓ"))
        assertEquals("7-Eleven", normalizePlaceName("７－Ｅｌｅｖｅｎ"))
    }

    @Test
    fun `keeps Chinese names as typed`() {
        assertEquals("林東芳牛肉麵", normalizePlaceName(" 林東芳牛肉麵 "))
    }

    @Test
    fun `same name ignores case beyond English letters`() {
        assertTrue(sameName("Starbucks", "starbucks"))
        assertTrue(sameName("Café", "CAFÉ"))
        assertTrue(sameName("Starbucks", "Starbucks "))
        assertTrue(sameName("Starbucks", "Ｓｔａｒｂｕｃｋｓ"))
        assertFalse(sameName("Starbucks", "Starbucks Reserve"))
    }

    @Test
    fun `query matches anywhere in the name`() {
        assertTrue(matchesQuery("Lin Dong Fang", "dong"))
        assertTrue(matchesQuery("Lin Dong Fang", ""))
        assertTrue(matchesQuery("林東芳牛肉麵", "牛肉"))
        assertFalse(matchesQuery("Lin Dong Fang", "noodle"))
    }

    @Test
    fun `a name starts with what was typed, ignoring case and width`() {
        assertTrue(startsWithQuery("林家乾麵", "林"))
        assertTrue(startsWithQuery("Simple Kaffa", " ｓｉｍ"))
        assertFalse(startsWithQuery("小林拉麵", "林"))
    }
}
