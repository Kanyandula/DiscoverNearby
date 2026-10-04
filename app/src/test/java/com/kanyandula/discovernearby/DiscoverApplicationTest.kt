package com.kanyandula.discovernearby

import com.kanyandula.discovernearby.car.CarDrivingRestrictions
import com.kanyandula.discovernearby.location.fake.FakeLocationProvider
import com.kanyandula.discovernearby.places.fake.FakePlacesRepository
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DiscoverApplicationTest {

    @Test
    fun applicationCreatesTheAppContainer() {
        val app = RuntimeEnvironment.getApplication() as DiscoverApplication
        assertNotNull(app.container)
    }

    @Test
    fun containerProvidesItsDependencies() {
        val container = (RuntimeEnvironment.getApplication() as DiscoverApplication).container
        assertTrue(container.placesRepository is FakePlacesRepository)
        assertTrue(container.locationProvider is FakeLocationProvider)
        assertTrue(container.drivingRestrictions is CarDrivingRestrictions)
    }
}
