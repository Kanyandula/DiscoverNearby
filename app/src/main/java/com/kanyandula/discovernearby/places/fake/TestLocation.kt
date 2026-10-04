package com.kanyandula.discovernearby.places.fake

import com.kanyandula.discovernearby.model.GeoPoint

/** docs/03 §7 test locations A–C, stored once. */
enum class TestLocation(val label: String, val point: GeoPoint) {
    GREYSTONES("Greystones", GeoPoint(lat = 53.1440, lng = -6.0633)),
    DUBLIN("Dublin", GeoPoint(lat = 53.3498, lng = -6.2603)),
    GALWAY("Galway", GeoPoint(lat = 53.2707, lng = -9.0568)),
    ;

    companion object {
        private const val MATCH_RADIUS_METERS = 30_000

        /** The test location whose fake data serves [origin], or null when none is within reach. */
        fun nearestTo(origin: GeoPoint): TestLocation? =
            entries.minBy { it.point.distanceMetersTo(origin) }
                .takeIf { it.point.distanceMetersTo(origin) <= MATCH_RADIUS_METERS }
    }
}
