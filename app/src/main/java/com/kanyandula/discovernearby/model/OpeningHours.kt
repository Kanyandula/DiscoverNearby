package com.kanyandula.discovernearby.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/** A weekly opening period: it starts at [start] on each of [days] and lasts [duration], possibly past midnight. */
data class OpeningPeriod(val days: Set<DayOfWeek>, val start: LocalTime, val duration: Duration)

/** When an open place closes today, as the opening line says it (DN-UX-005). */
sealed interface Closing {
    data class At(val time: LocalTime) : Closing
    data object AllDay : Closing
}

private val WHOLE_DAY: Duration = Duration.ofHours(24)
private const val DAYS_AHEAD = 7L // far enough to follow any weekly schedule round

/**
 * How the stretch open at [now] ends, or null when nothing is open: the latest closing among the periods open now,
 * carried on through any period that starts by then (HERE splits overnight hours at midnight: "18:00 - 24:00" and
 * "00:00 - 02:00"). [Closing.AllDay] when the stretch lasts a day or more from now. Yesterday's periods count too.
 */
fun List<OpeningPeriod>.closingAt(now: LocalDateTime): Closing? {
    val today = now.toLocalDate()
    val spans = flatMap { period ->
        (-1L..DAYS_AHEAD).map { today.plusDays(it) }
            .filter { it.dayOfWeek in period.days }
            .map { day -> day.atTime(period.start).let { opens -> opens to opens + period.duration } }
    }
    var end = spans.filter { (opens, closes) -> !now.isBefore(opens) && now.isBefore(closes) }
        .maxOfOrNull { it.second } ?: return null
    // Each step moves the end later, so this stops once no period carries it on.
    while (true) {
        end = spans.filter { (opens, closes) -> !opens.isAfter(end) && closes.isAfter(end) }
            .maxOfOrNull { it.second } ?: break
    }
    return if (Duration.between(now, end) >= WHOLE_DAY) Closing.AllDay else Closing.At(end.toLocalTime())
}
