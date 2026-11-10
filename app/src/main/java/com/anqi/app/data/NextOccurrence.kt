package com.anqi.app.data

import com.anqi.app.model.Alarm
import com.anqi.app.model.RepeatType
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime

/**
 * Pure, testable recurrence calculation. It intentionally skips invalid dates
 * (monthly 31st in February, Feb 29 in a common year) instead of silently
 * moving an occurrence to the next month.
 */
object NextOccurrence {

    fun next(
        alarm: Alarm,
        after: ZonedDateTime = ZonedDateTime.now()
    ): ZonedDateTime? {
        if (!alarm.enabled) return null
        if (alarm.remainingCount != null && alarm.remainingCount <= 0) return null

        val time = LocalTime.of(alarm.hour.coerceIn(0, 23), alarm.minute.coerceIn(0, 59))

        fun candidate(date: LocalDate): ZonedDateTime =
            ZonedDateTime.of(date, time, after.zone)

        fun allowed(date: LocalDate): Boolean =
            alarm.endDate == null || !date.isAfter(alarm.endDate)

        return when (alarm.repeatType) {
            RepeatType.ONCE -> {
                val date = alarm.date ?: return null
                if (!allowed(date)) return null
                candidate(date).takeIf { it.isAfter(after) }
            }

            RepeatType.DAILY -> {
                for (offset in 0..3660) {
                    val date = after.toLocalDate().plusDays(offset.toLong())
                    if (!allowed(date)) break
                    val value = candidate(date)
                    if (value.isAfter(after)) return value
                }
                null
            }

            RepeatType.WORKDAYS -> {
                for (offset in 0..3660) {
                    val date = after.toLocalDate().plusDays(offset.toLong())
                    if (!allowed(date)) break
                    if (date.dayOfWeek.value !in 1..5) continue
                    val value = candidate(date)
                    if (value.isAfter(after)) return value
                }
                null
            }

            RepeatType.WEEKLY -> {
                if (alarm.weekdays.isEmpty()) return null
                for (offset in 0..3660) {
                    val date = after.toLocalDate().plusDays(offset.toLong())
                    if (!allowed(date)) break
                    if (date.dayOfWeek.value !in alarm.weekdays) continue
                    val value = candidate(date)
                    if (value.isAfter(after)) return value
                }
                null
            }

            RepeatType.MONTHLY_DAY,
            RepeatType.MONTHLY_LAST_DAY,
            RepeatType.MONTHLY_NTH_WEEKDAY -> {
                val month = YearMonth.from(after)
                for (offset in 0..600) {
                    val yearMonth = month.plusMonths(offset.toLong())
                    val day = when (alarm.repeatType) {
                        RepeatType.MONTHLY_DAY -> alarm.monthlyDay
                        RepeatType.MONTHLY_LAST_DAY -> yearMonth.lengthOfMonth()
                        else -> nthWeekdayOfMonth(
                            yearMonth = yearMonth,
                            nth = alarm.nth ?: 1,
                            weekday = alarm.nthWeekday ?: 1
                        )
                    } ?: continue

                    if (day !in 1..yearMonth.lengthOfMonth()) continue
                    val date = yearMonth.atDay(day)
                    if (!allowed(date)) break
                    val value = candidate(date)
                    if (value.isAfter(after)) return value
                }
                null
            }

            RepeatType.YEARLY -> {
                val month = alarm.yearlyMonth ?: return null
                val day = alarm.yearlyDay ?: return null
                if (month !in 1..12 || day !in 1..31) return null
                for (offset in 0..200) {
                    val year = after.year + offset
                    if (!YearMonth.of(year, month).isValidDay(day)) continue
                    val date = LocalDate.of(year, month, day)
                    if (!allowed(date)) break
                    val value = candidate(date)
                    if (value.isAfter(after)) return value
                }
                null
            }
        }
    }

    fun nextN(
        alarm: Alarm,
        after: ZonedDateTime = ZonedDateTime.now(),
        count: Int = 3
    ): List<ZonedDateTime> {
        if (count <= 0) return emptyList()
        val limit = alarm.remainingCount?.let { minOf(count, it) } ?: count
        val result = mutableListOf<ZonedDateTime>()
        var cursor = after
        while (result.size < limit) {
            val item = next(alarm, cursor) ?: break
            result += item
            cursor = item.plusSeconds(1)
        }
        return result
    }

    private fun nthWeekdayOfMonth(
        yearMonth: YearMonth,
        nth: Int,
        weekday: Int
    ): Int? {
        if (weekday !in 1..7) return null
        if (nth == -1) {
            val last = yearMonth.atEndOfMonth()
            val diff = (last.dayOfWeek.value - weekday + 7) % 7
            return last.dayOfMonth - diff
        }
        if (nth !in 1..4) return null
        val first = yearMonth.atDay(1)
        val diff = (weekday - first.dayOfWeek.value + 7) % 7
        val day = 1 + diff + (nth - 1) * 7
        return day.takeIf { it <= yearMonth.lengthOfMonth() }
    }
}
