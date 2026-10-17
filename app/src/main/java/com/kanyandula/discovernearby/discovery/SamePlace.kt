package com.kanyandula.discovernearby.discovery

import com.kanyandula.discovernearby.model.PlaceSummary
import com.kanyandula.discovernearby.model.nameWords

/** Two records further apart than this are never one place, unless they share an ID. */
private const val SAME_PLACE_METERS = 150

/** Records this close with one phone number are one place, whatever their names. */
private const val SAME_SPOT_METERS = 10

/** A shorter name is never matched inside another. */
private const val MIN_NAME_LENGTH = 4

/**
 * Whether two provider records are one place (DN-M2-004, Product Lead 2026-10-11): the same ID; or within 150 m, a
 * shared phone number, and either within 10 m of each other or one name containing the other. HERE lists some places
 * twice under different IDs and names; a shared phone alone also joins neighbours with one owner.
 */
internal fun PlaceSummary.isSamePlaceAs(other: PlaceSummary): Boolean = when {
    id == other.id -> true
    phones.none { it in other.phones } -> false
    else -> location.distanceMetersTo(other.location).let { apart ->
        apart <= SAME_SPOT_METERS || (apart <= SAME_PLACE_METERS && namesOverlap(name, other.name))
    }
}

private fun namesOverlap(a: String, b: String): Boolean {
    val x = nameWords(a).joinToString(" ")
    val y = nameWords(b).joinToString(" ")
    return minOf(x.length, y.length) >= MIN_NAME_LENGTH && (x in y || y in x)
}
