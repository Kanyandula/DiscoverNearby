package com.kanyandula.discovernearby.car

import kotlinx.coroutines.flow.StateFlow

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
