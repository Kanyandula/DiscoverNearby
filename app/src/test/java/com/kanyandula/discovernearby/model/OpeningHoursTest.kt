package com.kanyandula.discovernearby.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

class OpeningHoursTest {

    private val weekdays = OpeningPeriod(
        setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY), LocalTime.of(7, 0), Duration.ofHours(9),
    )

    // October 2026: the 10th is a Saturday, the 11th a Sunday, the 12th a Monday.
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute)

    @Test
    fun anOpenPeriodGivesItsClosingTime() {
        assertEquals(Closing.At(LocalTime.of(16, 0)), listOf(weekdays).closingAt(at(12, 10)))
    }

    @Test
    fun beforeOpeningOrFromClosingThereIsNone() {
        assertNull(listOf(weekdays).closingAt(at(12, 6, 59)))
        assertNull(listOf(weekdays).closingAt(at(12, 16)))
    }

    @Test
    fun aDayNotListedHasNone() {
        assertNull(listOf(weekdays).closingAt(at(11, 10)))
    }

    // Review Focus 1
    @Test
    fun hoursPastMidnightCloseTheNextDay() {
        val late = OpeningPeriod(setOf(SATURDAY), LocalTime.of(20, 0), Duration.ofHours(6))
        assertEquals(Closing.At(LocalTime.of(2, 0)), listOf(late).closingAt(at(11, 1)))
        assertNull(listOf(late).closingAt(at(11, 2)))
    }

    @Test
    fun aWholeDayIsAllDay() {
        val always = OpeningPeriod(DayOfWeek.entries.toSet(), LocalTime.MIDNIGHT, Duration.ofHours(24))
        assertEquals(Closing.AllDay, listOf(always).closingAt(at(12, 3)))
    }

    // Split hours, or one entry per HERE category: the period open now decides; overlapping ones close at the latest.
    @Test
    fun thePeriodOpenNowDecides() {
        val lunch = OpeningPeriod(setOf(MONDAY), LocalTime.of(12, 0), Duration.ofHours(3))
        val dinner = OpeningPeriod(setOf(MONDAY), LocalTime.of(18, 0), Duration.ofHours(4))
        assertEquals(Closing.At(LocalTime.of(22, 0)), listOf(lunch, dinner).closingAt(at(12, 19)))
        assertEquals(Closing.At(LocalTime.of(16, 0)), listOf(lunch, weekdays).closingAt(at(12, 13)))
    }

    @Test
    fun noPeriodsHaveNone() {
        assertNull(emptyList<OpeningPeriod>().closingAt(at(12, 10)))
    }

    // Final review: HERE splits overnight hours at midnight ("Mon-Sat: 00:00 - 02:00, 18:00 - 24:00"), so the evening
    // period runs on into the next day's and the place closes at 02:00, not at midnight.
    @Test
    fun hoursSplitAtMidnightRunOn() {
        val days = setOf(SATURDAY, SUNDAY)
        val earlyHours = OpeningPeriod(days, LocalTime.MIDNIGHT, Duration.ofHours(2))
        val evening = OpeningPeriod(days, LocalTime.of(18, 0), Duration.ofHours(6))
        assertEquals(Closing.At(LocalTime.of(2, 0)), listOf(earlyHours, evening).closingAt(at(10, 23)))
    }

    // Back-to-back periods on one day are one stretch.
    @Test
    fun periodsThatTouchAreOneStretch() {
        val morning = OpeningPeriod(setOf(MONDAY), LocalTime.of(8, 0), Duration.ofHours(4))
        val afternoon = OpeningPeriod(setOf(MONDAY), LocalTime.of(12, 0), Duration.ofHours(6))
        assertEquals(Closing.At(LocalTime.of(18, 0)), listOf(morning, afternoon).closingAt(at(12, 9)))
    }

    // A single day-long period ends at midnight; only a stretch of a day or more from now is "open 24 hours".
    @Test
    fun oneDayLongPeriodClosesAtMidnight() {
        val monday = OpeningPeriod(setOf(MONDAY), LocalTime.MIDNIGHT, Duration.ofHours(24))
        assertEquals(Closing.At(LocalTime.MIDNIGHT), listOf(monday).closingAt(at(12, 10)))
    }
}
