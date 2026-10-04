@file:Suppress("MagicNumber") // Fixture coordinates from docs/03 §7.

package com.kanyandula.discovernearby.places.fake

import com.kanyandula.discovernearby.model.GeoPoint

/** docs/03 §7 test locations A–C, stored once. */
enum class TestLocation(val label: String, val point: GeoPoint) {
    GREYSTONES("Greystones", GeoPoint(53.1440, -6.0633)),
    DUBLIN("Dublin", GeoPoint(53.3498, -6.2603)),
    GALWAY("Galway", GeoPoint(53.2707, -9.0568)),
    ;

    companion object {
        private const val MATCH_RADIUS_METERS = 30_000

        /** The test location whose fake data serves [origin], or null when none is within reach. */
        fun nearestTo(origin: GeoPoint): TestLocation? =
            entries.minBy { it.point.distanceMetersTo(origin) }
                .takeIf { it.point.distanceMetersTo(origin) <= MATCH_RADIUS_METERS }
    }
}
