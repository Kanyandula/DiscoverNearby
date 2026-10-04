package com.kanyandula.discovernearby.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.serialization.Serializable

@Serializable
data class GeoPoint(val lat: Double, val lng: Double) {

    /** Great-circle (haversine) distance in metres. */
    fun distanceMetersTo(other: GeoPoint): Int {
        val dLat = Math.toRadians(other.lat - lat)
        val dLng = Math.toRadians(other.lng - lng)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat)) * cos(Math.toRadians(other.lat)) * sin(dLng / 2).pow(2)
        return (2 * EARTH_RADIUS_METERS * asin(sqrt(h))).roundToInt()
    }

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_008.8
    }
}
