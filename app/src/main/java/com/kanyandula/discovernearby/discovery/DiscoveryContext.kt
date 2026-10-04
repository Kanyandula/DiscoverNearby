package com.kanyandula.discovernearby.discovery

import com.kanyandula.discovernearby.model.GeoPoint

/** The single input to ranking (docs/03 §2, §15). New signals arrive as new optional fields. */
data class DiscoveryContext(
    val requestId: Long,
    val origin: GeoPoint,
    val category: DiscoveryCategory,
    val createdAtMillis: Long,
)
