package com.kanyandula.discovernearby.places.here

import com.kanyandula.discovernearby.model.OpeningPeriod
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val BY_DAY = Regex("""BYDAY:([A-Z,]+)""")
private val START = DateTimeFormatter.ofPattern("'T'HHmmss")
private val DAYS = DayOfWeek.entries.associateBy { it.name.take(2) } // "MO" → MONDAY

/** HERE's structured period as a domain one, or null when any part is unreadable: that period is left out. */
internal fun HereStructuredHours.toPeriod(): OpeningPeriod? {
    val codes = BY_DAY.find(recurrence)?.groupValues?.get(1)?.split(',').orEmpty()
    val days = codes.mapNotNull { DAYS[it] }.toSet()
    if (days.isEmpty() || days.size != codes.size) return null
    return try {
        OpeningPeriod(days, LocalTime.parse(start, START), Duration.parse(duration))
    } catch (ignored: DateTimeParseException) {
        null
    }
}
