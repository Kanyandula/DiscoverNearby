package com.kanyandula.discovernearby.navigation

import com.kanyandula.discovernearby.model.GeoPoint

/** Hands a destination to the vehicle's navigation app (docs/03 §11); never targets a specific app. */
interface NavigationLauncher {
    fun navigateTo(point: GeoPoint): Result<Unit>
}
