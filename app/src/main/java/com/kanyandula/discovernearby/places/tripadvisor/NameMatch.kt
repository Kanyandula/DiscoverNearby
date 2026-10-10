package com.kanyandula.discovernearby.places.tripadvisor

import java.text.Normalizer
import java.util.Locale

// Words that name the trade, not the place: "COSTA COFFEE" and "Costa" are one café. Place words such as "park" or
// "beach" stay, so a beach doesn't match the park next to it.
private val GENERIC_WORDS = setOf("the", "a", "an", "of", "and", "ltd", "co", "cafe", "coffee", "restaurant", "bar")
private const val MIN_SHARED_CHARS = 4
private const val MIN_SIMILARITY = 0.8
private const val SAME_WORDS = 3
private const val ONE_EXTRA_WORD = 2
private const val SIMILAR_SPELLING = 1

/**
 * How surely two providers' names are one place (DN-UX-004): 3 for the same words; 2 when the longer name adds one
 * word, such as a town ("Happy Pear" and "Happy Pear Greystones"); 1 for nearly the same spelling; 0 for no match.
 * A wrong match shows another place's photo and rating, which is worse than none, so a second extra word ("Harbour"
 * and "Harbour Lane Deli") is no match.
 */
internal fun nameMatch(a: String, b: String): Int {
    val wordsA = words(a)
    val wordsB = words(b)
    if (wordsA.isEmpty() || wordsB.isEmpty()) return 0
    val (shorter, longer) = if (wordsA.size <= wordsB.size) wordsA to wordsB else wordsB to wordsA
    val addsOneWord = longer.size - shorter.size == 1 && longer.containsAll(shorter)
    return when {
        shorter.toSet() == longer.toSet() -> SAME_WORDS
        addsOneWord && shorter.sumOf { it.length } >= MIN_SHARED_CHARS -> ONE_EXTRA_WORD
        similarity(wordsA.joinToString(" "), wordsB.joinToString(" ")) >= MIN_SIMILARITY -> SIMILAR_SPELLING
        else -> 0
    }
}

private fun words(name: String): List<String> =
    Normalizer.normalize(name, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}"), "") // accents: "Café" is "Cafe"
        .lowercase(Locale.ROOT)
        .split(Regex("[^a-z0-9]+"))
        .filter { it.isNotEmpty() && it !in GENERIC_WORDS }

/** 1 for the same text, 0 for nothing in common: one minus the edit distance over the longer length. */
private fun similarity(a: String, b: String): Double {
    var previous = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val current = IntArray(b.length + 1)
        current[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
        }
        previous = current
    }
    return 1.0 - previous[b.length].toDouble() / maxOf(a.length, b.length)
}
