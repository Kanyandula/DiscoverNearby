package com.kanyandula.discovernearby.places.here

import kotlinx.serialization.Serializable

// HERE Geocoding & Search v7 response shapes (ADR-001 [H2]), only the fields we map. Provider models never leave
// this package (docs/03 §8). Every field is optional, so one sparse item drops out instead of failing the response.

@Serializable
internal data class HereBrowseResponse(val items: List<HereItem> = emptyList())

@Serializable
internal data class HereItem(
    val id: String? = null,
    val title: String? = null,
    val position: HerePosition? = null,
    val categories: List<HereCategory> = emptyList(),
    val openingHours: List<HereOpeningHours> = emptyList(),
)

@Serializable
internal data class HerePosition(val lat: Double? = null, val lng: Double? = null)

@Serializable
internal data class HereCategory(val id: String = "", val primary: Boolean = false)

@Serializable
internal data class HereOpeningHours(val text: List<String> = emptyList(), val isOpen: Boolean? = null)
