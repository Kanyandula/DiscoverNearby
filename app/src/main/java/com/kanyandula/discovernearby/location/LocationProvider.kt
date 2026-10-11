package com.kanyandula.discovernearby.location

import com.kanyandula.discovernearby.model.GeoPoint

sealed interface LocationResult {
    data class Available(val point: GeoPoint) : LocationResult
    data object PermissionMissing : LocationResult
    data object Unavailable : LocationResult
}

/** Read only when the user requests discovery (docs/01 §14) or opens Saved (docs/03 §13). */
interface LocationProvider {
    suspend fun currentLocation(): LocationResult
}
