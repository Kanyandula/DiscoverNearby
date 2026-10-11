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

/**
 * How the periods open at [now] end, or null when none is open: [Closing.AllDay] when one lasts a whole day,
 * otherwise the latest closing among them. Yesterday's periods count, for hours that run past midnight.
 */
fun List<OpeningPeriod>.closingAt(now: LocalDateTime): Closing? {
    val today = now.toLocalDate()
    val open = flatMap { period ->
        listOf(today.minusDays(1), today)
            .filter { it.dayOfWeek in period.days }
            .map { day -> period to day.atTime(period.start) }
    }.filter { (period, opens) -> !now.isBefore(opens) && now.isBefore(opens + period.duration) }
    return when {
        open.isEmpty() -> null
        open.any { (period, _) -> period.duration >= WHOLE_DAY } -> Closing.AllDay
        else -> Closing.At(open.maxOf { (period, opens) -> opens + period.duration }.toLocalTime())
    }
}
