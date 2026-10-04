package com.kanyandula.discovernearby.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationCompat
import androidx.core.location.LocationManagerCompat
import com.kanyandula.discovernearby.model.GeoPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Asked for together; either is enough, so an approximate-only grant (Android 12+) still finds places. */
val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/** How long a read waits for a fix before reporting Unavailable (docs/02 §11: Retry and Back, never endless). */
const val LOCATION_TIMEOUT_MILLIS = 8_000L

/**
 * The vehicle's location from LocationManager (docs/03 §7), read only when discovery asks (docs/01 §14). GPS
 * first, network where a car has one; the reference image's Play-services fused provider adds nothing for one fix.
 */
class AndroidLocationProvider(
    private val context: Context,
    private val timeoutMillis: Long = LOCATION_TIMEOUT_MILLIS,
) : LocationProvider {

    private val locationManager = context.getSystemService(LocationManager::class.java)

    override suspend fun currentLocation(): LocationResult {
        val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        val approximate = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        val provider = PROVIDERS.firstOrNull { locationManager.isProviderEnabled(it) }
        val fix = when {
            provider == null || !(fine || approximate) -> null
            fine -> withTimeoutOrNull(timeoutMillis) { currentFix(provider) }
            // Approximate only: the platform turns the request into a low-power one that GPS never serves, so the
            // platform's recent (already coarsened) fix is the answer; a fresh request is the fallback.
            else -> recentFix(provider) ?: withTimeoutOrNull(timeoutMillis) { currentFix(provider) }
        }
        return when {
            !(fine || approximate) -> LocationResult.PermissionMissing
            fix == null -> LocationResult.Unavailable
            else -> LocationResult.Available(GeoPoint(fix.latitude, fix.longitude))
        }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    // currentLocation() checks the permission first; revoking it ends the process, so nothing can race it.
    @SuppressLint("MissingPermission")
    private fun recentFix(provider: String): Location? = locationManager.getLastKnownLocation(provider)?.takeIf {
        SystemClock.elapsedRealtime() - LocationCompat.getElapsedRealtimeMillis(it) <= RECENT_FIX_MILLIS
    }

    // currentLocation() checks the permission first; revoking it ends the process, so nothing can race it.
    @SuppressLint("MissingPermission")
    private suspend fun currentFix(provider: String): Location? = suspendCancellableCoroutine { continuation ->
        val cancel = CancellationSignal()
        continuation.invokeOnCancellation { cancel.cancel() }
        LocationManagerCompat.getCurrentLocation(locationManager, provider, cancel, Runnable::run) {
            continuation.resume(it)
        }
    }

    private companion object {
        val PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

        // The platform's update interval for approximate-only apps.
        const val RECENT_FIX_MILLIS = 10 * 60_000L
    }
}
