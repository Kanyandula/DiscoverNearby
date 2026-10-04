package com.kanyandula.discovernearby.car

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/**
 * [DrivingRestrictions] from CarUxRestrictionsManager (NyasaPlayer's CarUxRestrictionsHandler pattern).
 * The only file that imports android.car. Connected while [state] is collected; disconnected
 * [STOP_TIMEOUT_MILLIS] after the last collector stops.
 */
class CarDrivingRestrictions(context: Context, scope: CoroutineScope) : DrivingRestrictions {

    private val appContext = context.applicationContext

    override val state: StateFlow<DrivingState> = callbackFlow {
        val car = connectOrNull()
        val manager = car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
        if (car == null || manager == null) {
            Log.w(TAG, "UX restrictions unavailable; assuming restrictions apply")
            send(UNKNOWN_DRIVING_STATE)
            awaitClose { car?.disconnect() }
            return@callbackFlow
        }
        check(UxFlags.LIMIT_CONTENT == CarUxRestrictions.UX_RESTRICTIONS_LIMIT_CONTENT) { "LIMIT_CONTENT drift" }
        Log.i(TAG, "connected")
        trySend(manager.currentCarUxRestrictions.toDrivingState())
        manager.registerListener { trySend(it.toDrivingState()) }
        awaitClose {
            manager.unregisterListener()
            car.disconnect()
            Log.i(TAG, "disconnected")
        }
    }
        // Anything the Car stack throws (including a missing class off-device) becomes the fallback.
        .catch { e ->
            Log.w(TAG, "UX restrictions failed; assuming restrictions apply", e)
            emit(UNKNOWN_DRIVING_STATE)
        }
        .onEach { Log.i(TAG, "UX restrictions: $it") }
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UNKNOWN_DRIVING_STATE)

    /** Null off-device or when the Car service is unavailable (Car.createCar returns null then). */
    private fun connectOrNull(): Car? =
        if (appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            Car.createCar(appContext)
        } else {
            null
        }

    private fun CarUxRestrictions.toDrivingState() =
        drivingState(isRequiresDistractionOptimization, activeRestrictions, maxCumulativeContentItems)

    private companion object {
        const val TAG = "DrivingRestrictions"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
