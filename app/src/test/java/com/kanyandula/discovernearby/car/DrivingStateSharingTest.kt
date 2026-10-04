package com.kanyandula.discovernearby.car

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class) // runCurrent, advanceTimeBy
class DrivingStateSharingTest {

    private val unrestricted = DrivingState(distractionOptimizationRequired = false, listLimit = null)
    private val upstream = flow {
        emit(unrestricted)
        awaitCancellation()
    }

    // A screen returning after the disconnect must not start from the last (possibly stale) state.
    @Test
    fun returningCollectorStartsFromUnknownNotTheLastState() = runTest {
        val state = upstream.shareAsDrivingState(backgroundScope)
        val collector = backgroundScope.launch { state.collect {} }
        runCurrent()
        assertEquals(unrestricted, state.value)

        collector.cancel()
        advanceTimeBy(DRIVING_STATE_STOP_TIMEOUT_MILLIS + 1)
        runCurrent()
        assertEquals(UNKNOWN_DRIVING_STATE, state.value)
    }

    @Test
    fun valueIsKeptWithinTheStopTimeout() = runTest {
        val state = upstream.shareAsDrivingState(backgroundScope)
        val collector = backgroundScope.launch { state.collect {} }
        runCurrent()
        collector.cancel()
        advanceTimeBy(DRIVING_STATE_STOP_TIMEOUT_MILLIS - 1)
        runCurrent()
        assertEquals(unrestricted, state.value)
    }
}
