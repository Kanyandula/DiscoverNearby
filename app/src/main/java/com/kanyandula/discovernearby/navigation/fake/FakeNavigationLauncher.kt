package com.kanyandula.discovernearby.navigation.fake

import com.kanyandula.discovernearby.model.GeoPoint
import com.kanyandula.discovernearby.navigation.NavigationLauncher

/** Records the destination and reports success; the M0 stand-in for the real hand-off. */
class FakeNavigationLauncher : NavigationLauncher {
    var lastDestination: GeoPoint? = null
        private set

    override fun navigateTo(point: GeoPoint): Result<Unit> {
        lastDestination = point
        return Result.success(Unit)
    }
}
