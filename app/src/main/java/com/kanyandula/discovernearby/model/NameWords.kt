package com.kanyandula.discovernearby.model

import java.text.Normalizer
import java.util.Locale

private val ACCENTS = Regex("\\p{M}")
private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")

/** A place name's words: accents dropped ("Café" is "cafe"), lower case, letters and digits only. */
internal fun nameWords(name: String): List<String> =
    Normalizer.normalize(name, Normalizer.Form.NFKD).replace(ACCENTS, "").lowercase(Locale.ROOT)
        .split(NON_ALPHANUMERIC).filter { it.isNotEmpty() }
