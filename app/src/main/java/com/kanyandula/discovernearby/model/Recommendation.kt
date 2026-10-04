package com.kanyandula.discovernearby.model

import kotlinx.serialization.Serializable

@Serializable
data class Recommendation(
    val place: PlaceSummary,
    val score: Double,
    val distanceMeters: Int,
    val travelTimeMinutes: Int?, // provider-supplied or calculated
    val minutesAhead: Int?, // stretch
    val detourMinutes: Int?, // stretch
)
