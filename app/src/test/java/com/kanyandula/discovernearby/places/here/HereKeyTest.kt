package com.kanyandula.discovernearby.places.here

import com.kanyandula.discovernearby.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class HereKeyTest {

    // docs/03 §19: the dev-only key comes from local.properties through BuildConfig. Properties keeps trailing
    // spaces, which HERE would reject, so the build trims it. Empty on CI, where there is no local.properties.
    @Test
    fun theKeyComesFromBuildConfigTrimmed() {
        val key: String = BuildConfig.HERE_API_KEY
        assertEquals(key.trim(), key)
    }
}
