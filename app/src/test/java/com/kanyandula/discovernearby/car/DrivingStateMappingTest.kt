package com.kanyandula.discovernearby.car

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The platform type is compileOnly (absent here), so the mapping is tested through raw values;
 * CarDrivingRestrictions checks the mirrored flag against the platform when it connects.
 */
class DrivingStateMappingTest {

    private val fullyRestrictedOnApi33 = 255 // what the reference emulator reports while moving

    @Test
    fun unrestrictedIgnoresTheBaselineItemCount() {
        val state = drivingState(false, activeRestrictions = 0, maxCumulativeContentItems = 21)
        assertEquals(DrivingState(false, null), state)
    }

    @Test
    fun limitContentReportsTheLimit() {
        assertEquals(DrivingState(true, 21), drivingState(true, fullyRestrictedOnApi33, maxCumulativeContentItems = 21))
    }

    // NyasaPlayer saw only NO_VIDEO (16) while idling: optimization may be required with no list limit.
    @Test
    fun restrictedWithoutLimitContentHasNoLimit() {
        val state = drivingState(true, activeRestrictions = 16, maxCumulativeContentItems = 21)
        assertEquals(DrivingState(true, null), state)
    }

    // The value crosses a trust boundary (vehicle HAL); List.take() throws on a negative count.
    @Test
    fun negativeLimitIsClampedToZero() {
        assertEquals(DrivingState(true, 0), drivingState(true, UxFlags.LIMIT_CONTENT, maxCumulativeContentItems = -3))
    }

    @Test
    fun limitFollowsTheFlagNotTheOptimizationAnswer() {
        val state = drivingState(false, UxFlags.LIMIT_CONTENT, maxCumulativeContentItems = 10)
        assertEquals(DrivingState(false, 10), state)
    }

    // Unknown restrictions: assume they apply, so Grant and other restricted actions stay hidden.
    @Test
    fun unknownStateAssumesRestrictionsApply() {
        assertEquals(DrivingState(true, null), UNKNOWN_DRIVING_STATE)
    }

    @Test
    fun mirroredLimitContentFlagMatchesTheSdkValue() {
        assertEquals(32, UxFlags.LIMIT_CONTENT) // android.car.drivingstate.CarUxRestrictions, platform 37
    }
}
