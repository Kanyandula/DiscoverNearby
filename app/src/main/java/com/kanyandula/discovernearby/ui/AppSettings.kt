package com.kanyandula.discovernearby.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import com.kanyandula.discovernearby.location.LOCATION_PERMISSIONS

/** The system's details page for this app, where location can be allowed again (docs/02 §10). */
internal fun appSettingsIntent(packageName: String) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

/** Opens [appSettingsIntent]; false when nothing on the system can show it. */
internal fun openAppSettings(context: Context): Boolean =
    runCatching { context.startActivity(appSettingsIntent(context.packageName)) }.isSuccess

/**
 * Whether [result] is a refusal Android won't ask about again: nothing granted, and no rationale offered for either
 * location permission (Android stops asking after two refusals). An empty result is a cancelled or overlapping
 * request, not a refusal.
 */
internal fun refusedForGood(activity: Activity, result: Map<String, Boolean>) =
    result.isNotEmpty() && true !in result.values &&
        LOCATION_PERMISSIONS.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
