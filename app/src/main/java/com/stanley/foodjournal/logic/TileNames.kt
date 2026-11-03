package com.stanley.foodjournal.logic

private val BreakAfter = Regex("""[\s\-‐–—/]+""")

private val BreakAnywhere = setOf(
    Character.UnicodeScript.HAN,
    Character.UnicodeScript.HIRAGANA,
    Character.UnicodeScript.KATAKANA,
    Character.UnicodeScript.HANGUL,
)

/**
 * The words of a tile's name that a line can't break inside, short of hyphenating: "Late-night
 * snack" gives "Late", "night", "snack". Chinese, Japanese, and Korean text can break between any
 * two characters, so words holding it are left out.
 */
fun unbreakableWords(name: String): List<String> =
    name.split(BreakAfter).filter { word ->
        word.isNotEmpty() && word.codePoints().noneMatch { Character.UnicodeScript.of(it) in BreakAnywhere }
    }
