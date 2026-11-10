package com.anqi.app.data

import com.anqi.app.model.Alarm
import java.time.LocalDate
import java.time.ZonedDateTime

/** One projected firing of an existing alarm. */
data class CalendarOccurrence(
    val alarm: Alarm,
    val at: ZonedDateTime
) {
    val date: LocalDate get() = at.toLocalDate()
}

/**
 * Projects recurring alarms onto calendar dates. This is deliberately a pure
 * read-only layer: it never schedules, persists, or replays an alarm.
 *
 * Only occurrences at or after [now] are returned. The calendar is a forward
 * view, not a history log, so editing a rule can never rewrite the past.
 */
object CalendarProjector {

    private const val MAX_PER_ALARM = 1_000

    fun occurrences(
        alarms: List<Alarm>,
        from: LocalDate,
        until: LocalDate,
        now: ZonedDateTime = ZonedDateTime.now()
    ): List<CalendarOccurrence> {
        if (until.isBefore(from)) return emptyList()

        val zone = now.zone
        val rangeStart = from.atStartOfDay(zone)
        val start = if (rangeStart.isAfter(now)) rangeStart else now
        val endExclusive = until.plusDays(1).atStartOfDay(zone)
        if (!start.isBefore(endExclusive)) return emptyList()

        val result = mutableListOf<CalendarOccurrence>()
        alarms.asSequence()
            .filter { it.enabled }
            .forEach { alarm ->
                // Back up one second so an occurrence exactly at the range
                // start is still considered.
                var cursor = start.minusSeconds(1)
                var guard = 0
                var added = 0
                while (guard++ < MAX_PER_ALARM) {
                    val next = NextOccurrence.next(alarm, cursor) ?: break
                    if (!next.isBefore(endExclusive)) break
                    if (!next.isBefore(start)) {
                        result += CalendarOccurrence(alarm, next)
                        added++
                        if (alarm.remainingCount != null && added >= alarm.remainingCount) break
                    }
                    cursor = next.plusSeconds(1)
                }
            }

        return result.sortedWith(compareBy({ it.at }, { it.alarm.id }))
    }

    fun byDay(
        alarms: List<Alarm>,
        from: LocalDate,
        until: LocalDate,
        now: ZonedDateTime = ZonedDateTime.now()
    ): Map<LocalDate, List<CalendarOccurrence>> =
        occurrences(alarms, from, until, now).groupBy { it.date }

    /** Monday-based start of the week containing [date]. */
    fun weekStart(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value - 1).toLong())
}
