package com.kanyandula.discovernearby

import org.junit.Assert.assertNotNull
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
}
