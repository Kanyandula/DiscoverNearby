package com.kanyandula.discovernearby.car

import kotlinx.coroutines.flow.MutableStateFlow

/** Test stand-in: set `state.value` to change the driving state; `state.subscriptionCount` shows collectors. */
class FakeDrivingRestrictions(
    override val state: MutableStateFlow<DrivingState> =
        MutableStateFlow(DrivingState(distractionOptimizationRequired = false, listLimit = null)),
) : DrivingRestrictions
