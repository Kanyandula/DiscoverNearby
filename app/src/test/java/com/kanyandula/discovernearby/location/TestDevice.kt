package com.kanyandula.discovernearby.location

import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import com.kanyandula.discovernearby.model.GeoPoint
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * Sets up Robolectric's device: the granted location [permissions], location on or off, and a GPS fix at [point]
 * (none when null) taken [fixAgeMillis] ago.
 */
fun deviceAt(
    point: GeoPoint?,
    permissions: Array<String> = LOCATION_PERMISSIONS,
    locationOn: Boolean = true,
    fixAgeMillis: Long = 0,
) {
    val app = RuntimeEnvironment.getApplication()
    shadowOf(app).grantPermissions(*permissions)
    val locationManager = shadowOf(app.getSystemService(LocationManager::class.java))
    locationManager.setLocationEnabled(locationOn)
    locationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, locationOn)
    point?.let {
        locationManager.simulateLocation(
            Location(LocationManager.GPS_PROVIDER).apply {
                latitude = it.lat
                longitude = it.lng
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            },
        )
    }
    ShadowSystemClock.advanceBy(Duration.ofMillis(fixAgeMillis))
}
