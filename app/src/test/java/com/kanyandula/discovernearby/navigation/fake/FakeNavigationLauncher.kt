package com.kanyandula.discovernearby.navigation.fake

import com.kanyandula.discovernearby.model.GeoPoint
import com.kanyandula.discovernearby.navigation.NavigationLauncher

/** Records the destination; fails with [failure] when set, as a missing or refusing navigation app would. */
class FakeNavigationLauncher : NavigationLauncher {
    var lastDestination: GeoPoint? = null
        private set
    var failure: Throwable? = null

    override fun navigateTo(point: GeoPoint): Result<Unit> {
        lastDestination = point
        return failure?.let { Result.failure(it) } ?: Result.success(Unit)
    }
}
