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
 * After a refusal: whether Android will no longer show the permission dialog. It stops after two refusals, and
 * then offers no rationale for either location permission.
 */
internal fun neverAsksAgain(activity: Activity) =
    LOCATION_PERMISSIONS.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
