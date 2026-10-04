package com.kanyandula.discovernearby.model

import kotlinx.serialization.Serializable

enum class AttributeType {
    PARKING, TOILETS, CAFE, PLAYGROUND, TRAILS, BEACH,
    VIEWPOINT, MUSEUM, FAMILY_FRIENDLY, DRIVE_THROUGH,
}

enum class AttributeSource {
    PROVIDED, // provider states it
    DERIVED, // reliably inferred from place type
}
// "Unavailable" = attribute absent from the set. It is never displayed.

@Serializable
data class PlaceAttribute(
    val type: AttributeType,
    val source: AttributeSource,
)
