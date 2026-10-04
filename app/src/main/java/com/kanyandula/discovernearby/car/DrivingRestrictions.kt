package com.kanyandula.discovernearby.car

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * The car's UX restrictions as the UI needs them (docs/03 §6). This reports restrictions, not the
 * gear: AOSP tells apps to monitor UX restrictions rather than an absolute driving state.
 */
data class DrivingState(
    val distractionOptimizationRequired: Boolean,
    val listLimit: Int?, // null = no list limit in force
)

interface DrivingRestrictions {
    val state: StateFlow<DrivingState>
}

/** Before the Car service answers, or when it is unavailable: assume restrictions apply. */
val UNKNOWN_DRIVING_STATE = DrivingState(distractionOptimizationRequired = true, listLimit = null)

/**
 * Mirrors android.car.drivingstate.CarUxRestrictions values: android.car.jar is compileOnly and
 * absent from unit tests. CarDrivingRestrictions checks it against the platform on connect.
 */
internal object UxFlags {
    const val LIMIT_CONTENT = 32
}

/**
 * Pure mapping from the platform's raw values. The platform reports maxCumulativeContentItems while
 * unrestricted too (a baseline, not a limit), so the LIMIT_CONTENT bit decides whether it applies.
 * The value comes from the vehicle HAL, so it is clamped at zero.
 */
internal fun drivingState(
    requiresDistractionOptimization: Boolean,
    activeRestrictions: Int,
    maxCumulativeContentItems: Int,
): DrivingState = DrivingState(
    distractionOptimizationRequired = requiresDistractionOptimization,
    listLimit = maxCumulativeContentItems.coerceAtLeast(0)
        .takeIf { activeRestrictions and UxFlags.LIMIT_CONTENT != 0 },
)

/** How long the restrictions stay shared after the last collector stops (survives a quick restart). */
const val DRIVING_STATE_STOP_TIMEOUT_MILLIS = 5_000L

/**
 * Shares restriction updates while collected. Once the last collector has been gone for
 * [DRIVING_STATE_STOP_TIMEOUT_MILLIS] the source stops and the value resets to [UNKNOWN_DRIVING_STATE]:
 * a screen returning later must not start from a stale state (e.g. unrestricted while the car now moves).
 */
internal fun Flow<DrivingState>.shareAsDrivingState(scope: CoroutineScope): StateFlow<DrivingState> =
    stateIn(
        scope,
        SharingStarted.WhileSubscribed(DRIVING_STATE_STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0),
        UNKNOWN_DRIVING_STATE,
    )
