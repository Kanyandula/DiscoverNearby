package com.kanyandula.discovernearby.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class ClockTimeTest {

    // The car's 12/24-hour setting decides, as the system clock shows it.
    @Test
    fun followsTheCarsClockSetting() {
        assertEquals("18:00", clockTime(LocalTime.of(18, 0), is24Hour = true))
        assertEquals("6:00 PM", clockTime(LocalTime.of(18, 0), is24Hour = false))
        assertEquals("00:30", clockTime(LocalTime.of(0, 30), is24Hour = true))
        assertEquals("12:30 AM", clockTime(LocalTime.of(0, 30), is24Hour = false))
    }
}
