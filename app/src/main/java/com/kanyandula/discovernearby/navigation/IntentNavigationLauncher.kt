package com.kanyandula.discovernearby.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.kanyandula.discovernearby.model.GeoPoint

/** `geo:lat,lng` to six decimals; Locale.US, so a comma-decimal locale can't reach the URI (docs/03 §11). */
internal fun geoUri(point: GeoPoint): String = "geo:${point.latLng()}"

/** AAOS's navigation intent, which car navigation apps must handle (DN-SP-006); a string, so no Car App Library. */
internal const val ACTION_NAVIGATE = "androidx.car.app.action.NAVIGATE"

/**
 * Hands the destination to whatever navigation app the system resolves (docs/03 §11), never a named app: first
 * [ACTION_NAVIGATE], which starts a route; if that fails, ACTION_VIEW, for apps that only show a geo: point. Takes the
 * application Context, so starting needs FLAG_ACTIVITY_NEW_TASK. When both fail, from ActivityNotFoundException (no
 * navigation app) to SecurityException, the result is Result.failure.
 */
class IntentNavigationLauncher(private val appContext: Context) : NavigationLauncher {
    override fun navigateTo(point: GeoPoint): Result<Unit> {
        val uri = geoUri(point).toUri()
        return runCatching { start(ACTION_NAVIGATE, uri) }.recoverCatching { start(Intent.ACTION_VIEW, uri) }
    }

    private fun start(action: String, uri: Uri) =
        appContext.startActivity(Intent(action, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
