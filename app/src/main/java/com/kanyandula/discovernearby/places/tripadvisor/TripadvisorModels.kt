package com.kanyandula.discovernearby.places.tripadvisor

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Tripadvisor Terra responses, only the fields the enricher reads. They never leave this package (docs/03 §3).

@Serializable
internal data class NearbyResponse(val data: List<NearbyItem> = emptyList())

@Serializable
internal data class NearbyItem(val location: TripadvisorLocation? = null)

@Serializable
internal data class TripadvisorLocation(
    val id: Long? = null,
    val names: List<LocationName> = emptyList(),
    @SerialName("traveler_ratings") val travelerRatings: TravelerRatings? = null,
)

@Serializable
internal data class LocationName(val value: String? = null)

@Serializable
internal data class TravelerRatings(val overall: OverallRating? = null)

@Serializable
internal data class OverallRating(
    val rating: Double? = null,
    val count: Int? = null,
    @SerialName("icon_url") val iconUrl: String? = null,
)

@Serializable
internal data class PhotosResponse(val data: List<PhotoItem> = emptyList())

@Serializable
internal data class PhotoItem(val photo: Photo? = null)

@Serializable
internal data class Photo(@SerialName("original_size_url") val url: String? = null)
