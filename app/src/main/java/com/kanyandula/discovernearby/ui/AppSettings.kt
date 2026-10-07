package com.kanyandula.discovernearby.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.kanyandula.discovernearby.location.LOCATION_PERMISSIONS

/** The system's details page for this app, where location can be allowed again (docs/02 §10). */
internal fun appSettingsIntent(packageName: String) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

/** Opens [appSettingsIntent]; false when nothing on the system can show it. */
internal fun openAppSettings(context: Context): Boolean =
    runCatching { context.startActivity(appSettingsIntent(context.packageName)) }.isSuccess

/**
 * Open Settings for a composable: opens [appSettingsIntent], and runs [onReturn] once when the app resumes after it,
 * in case location was allowed there. Nothing runs on return if Settings didn't open. The flag is saveable, so a
 * UI rebuilt from saved state while Settings is in front still runs [onReturn].
 */
@Composable
internal fun rememberOpenAppSettings(onReturn: () -> Unit): () -> Unit {
    val context = LocalContext.current
    var opened by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (opened) {
            opened = false
            onReturn()
        }
    }
    return { opened = openAppSettings(context) }
}

/**
 * Whether [result] is a refusal Android won't ask about again: nothing granted, and no rationale offered for either
 * location permission (after two refusals on Android 11+, or "Deny & don't ask again" on Android 10). An empty
 * result is a cancelled or overlapping request, not a refusal.
 */
internal fun refusedForGood(activity: Activity, result: Map<String, Boolean>) =
    result.isNotEmpty() && true !in result.values &&
        LOCATION_PERMISSIONS.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
