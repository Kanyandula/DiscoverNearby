package com.kanyandula.discovernearby.location

import com.kanyandula.discovernearby.model.GeoPoint

sealed interface LocationResult {
    data class Available(val point: GeoPoint) : LocationResult
    data object PermissionMissing : LocationResult
    data object Unavailable : LocationResult
}

/** Read only when the user requests discovery (docs/01 §14). AndroidLocationProvider arrives in DN-M0-006. */
interface LocationProvider {
    suspend fun currentLocation(): LocationResult
}
