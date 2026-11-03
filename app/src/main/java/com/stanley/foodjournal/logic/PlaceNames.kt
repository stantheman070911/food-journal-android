package com.stanley.foodjournal.logic

import java.text.Normalizer
import java.util.Locale

private val Whitespace = Regex("\\s+")

/**
 * The form every place name is saved and compared in: Unicode NFKC, which turns full-width
 * letters and digits into plain ones, with the ends trimmed and runs of spaces collapsed to one.
 */
fun normalizePlaceName(raw: String): String =
    Normalizer.normalize(raw, Normalizer.Form.NFKC).trim().replace(Whitespace, " ")

/** Whether two place names refer to the same place, ignoring case in any script. */
fun sameName(a: String, b: String): Boolean = nameKey(a) == nameKey(b)

/** Whether [name] contains what the user has typed, ignoring case and width. */
fun matchesQuery(name: String, query: String): Boolean {
    val key = nameKey(query)
    return key.isEmpty() || nameKey(name).contains(key)
}

private fun nameKey(name: String): String = normalizePlaceName(name).lowercase(Locale.ROOT)
