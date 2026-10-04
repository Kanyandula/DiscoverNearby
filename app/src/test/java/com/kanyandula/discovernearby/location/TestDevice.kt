package com.kanyandula.discovernearby.location

import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import com.kanyandula.discovernearby.model.GeoPoint
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Sets up Robolectric's device: the granted location [permissions], location on or off, and a fresh GPS fix at
 * [point] (none when null).
 */
fun deviceAt(point: GeoPoint?, permissions: Array<String> = LOCATION_PERMISSIONS, locationOn: Boolean = true) {
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
}
