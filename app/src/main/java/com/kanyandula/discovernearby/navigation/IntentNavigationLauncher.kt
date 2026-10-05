package com.kanyandula.discovernearby.navigation

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.kanyandula.discovernearby.model.GeoPoint
import java.util.Locale

/** `geo:lat,lng` to six decimals; Locale.US, so a comma-decimal locale can't reach the URI (docs/03 §11). */
internal fun geoUri(point: GeoPoint): String = String.format(Locale.US, "geo:%.6f,%.6f", point.lat, point.lng)

/**
 * Hands the destination to whatever navigation app the system resolves (docs/03 §11): ACTION_VIEW with a geo: URI,
 * never a named app. Takes the application Context, so starting needs FLAG_ACTIVITY_NEW_TASK. Any failure, from
 * ActivityNotFoundException (no navigation app) to SecurityException, comes back as Result.failure.
 */
class IntentNavigationLauncher(private val appContext: Context) : NavigationLauncher {
    override fun navigateTo(point: GeoPoint): Result<Unit> = runCatching {
        appContext.startActivity(
            Intent(Intent.ACTION_VIEW, geoUri(point).toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
