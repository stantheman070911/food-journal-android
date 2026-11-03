package com.stanley.foodjournal.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class TileNamesTest {
    @Test
    fun splitsAtSpacesHyphensAndSlashes() {
        assertEquals(listOf("Late", "night", "snack"), unbreakableWords("Late-night snack"))
        assertEquals(listOf("Tea", "coffee"), unbreakableWords("Tea / coffee"))
        assertEquals(listOf("Oyster", "omelette"), unbreakableWords("  Oyster   omelette "))
    }

    @Test
    fun leavesOutWordsThatCanBreakBetweenCharacters() {
        assertEquals(emptyList<String>(), unbreakableWords("牛肉麵"))
        assertEquals(listOf("Pho"), unbreakableWords("Pho 越南河粉"))
        assertEquals(emptyList<String>(), unbreakableWords("ラーメン 김치"))
    }

    @Test
    fun anEmptyNameHasNoWords() {
        assertEquals(emptyList<String>(), unbreakableWords(""))
    }
}
