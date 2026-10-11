package com.kanyandula.discovernearby.places.here

import com.kanyandula.discovernearby.model.OpeningPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Duration
import java.time.LocalTime

// HERE's structured hours, in the shape a live /browse returned on 2026-10-11 (values invented).
class HereOpeningHoursTest {

    @Test
    fun heresShapeBecomesAPeriod() {
        val hours = HereStructuredHours("T070000", "PT09H00M", "FREQ:DAILY;BYDAY:MO,TU,WE,TH,FR")
        assertEquals(
            OpeningPeriod(setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY), LocalTime.of(7, 0), Duration.ofHours(9)),
            hours.toPeriod(),
        )
    }

    @Test
    fun minutesCount() {
        val hours = HereStructuredHours("T103000", "PT05H30M", "FREQ:DAILY;BYDAY:SU")
        assertEquals(
            OpeningPeriod(setOf(SUNDAY), LocalTime.of(10, 30), Duration.ofMinutes(330)),
            hours.toPeriod(),
        )
    }

    // An unreadable part drops that period only; the rest of the schedule still counts.
    @Test
    fun anUnreadablePartDropsThePeriod() {
        assertNull(HereStructuredHours("T070000", "PT09H00M", "FREQ:DAILY").toPeriod())
        assertNull(HereStructuredHours("T7", "PT09H00M", "FREQ:DAILY;BYDAY:MO").toPeriod())
        assertNull(HereStructuredHours("T070000", "9 hours", "FREQ:DAILY;BYDAY:MO").toPeriod())
        assertNull(HereStructuredHours("T070000", "PT09H00M", "FREQ:DAILY;BYDAY:XX").toPeriod())
        assertNull(HereStructuredHours().toPeriod())
    }
}
