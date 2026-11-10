package com.anqi.app

import com.anqi.app.data.NextOccurrence
import com.anqi.app.model.Alarm
import com.anqi.app.model.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class NextOccurrenceTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): ZonedDateTime =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)

    private fun monthly(day: Int) = Alarm(
        hour = 7,
        minute = 30,
        repeatType = RepeatType.MONTHLY_DAY,
        monthlyDay = day
    )

    @Test
    fun monthlyDay_findsNextMonth() {
        val next = NextOccurrence.next(monthly(8), at(2026, 11, 10))
        assertEquals(at(2026, 12, 8, 7, 30), next)
    }

    @Test
    fun monthlyDay_skipsMonthWithoutThatDay() {
        val next = NextOccurrence.next(monthly(31), at(2026, 2, 1))
        assertEquals(at(2026, 3, 31, 7, 30), next)
    }

    @Test
    fun lastDay_handlesLeapFebruary() {
        val alarm = Alarm(
            hour = 9,
            repeatType = RepeatType.MONTHLY_LAST_DAY
        )
        val next = NextOccurrence.next(alarm, at(2028, 2, 1))
        assertEquals(at(2028, 2, 29, 9, 0), next)
    }

    @Test
    fun nthWeekday_findsSecondWednesday() {
        val alarm = Alarm(
            hour = 8,
            repeatType = RepeatType.MONTHLY_NTH_WEEKDAY,
            nth = 2,
            nthWeekday = 3
        )
        val next = NextOccurrence.next(alarm, at(2026, 11, 1))
        assertEquals(at(2026, 11, 11, 8, 0), next)
    }

    @Test
    fun nthWeekday_findsLastFriday() {
        val alarm = Alarm(
            hour = 18,
            repeatType = RepeatType.MONTHLY_NTH_WEEKDAY,
            nth = -1,
            nthWeekday = 5
        )
        val next = NextOccurrence.next(alarm, at(2026, 11, 1))
        assertEquals(at(2026, 11, 27, 18, 0), next)
    }

    @Test
    fun yearly_feb29SkipsCommonYears() {
        val alarm = Alarm(
            hour = 10,
            repeatType = RepeatType.YEARLY,
            yearlyMonth = 2,
            yearlyDay = 29
        )
        val next = NextOccurrence.next(alarm, at(2026, 3, 1))
        assertEquals(at(2028, 2, 29, 10, 0), next)
    }

    @Test
    fun workdays_skipsWeekend() {
        val alarm = Alarm(hour = 8, repeatType = RepeatType.WORKDAYS)
        val next = NextOccurrence.next(alarm, at(2026, 11, 13, 8, 1))
        assertEquals(at(2026, 11, 16, 8, 0), next)
    }

    @Test
    fun endDate_stopsRecurrence() {
        val alarm = monthly(8).copy(endDate = LocalDate.of(2026, 12, 7))
        assertNull(NextOccurrence.next(alarm, at(2026, 11, 10)))
    }

    @Test
    fun remainingCountZero_stopsRecurrence() {
        assertNull(NextOccurrence.next(monthly(8).copy(remainingCount = 0), at(2026, 11, 1)))
    }

    @Test
    fun oneTimeInPast_returnsNull() {
        val alarm = Alarm(repeatType = RepeatType.ONCE, date = LocalDate.of(2026, 1, 1))
        assertNull(NextOccurrence.next(alarm, at(2026, 11, 1)))
    }

    @Test
    fun nextN_returnsThreeOrderedOccurrences() {
        val result = NextOccurrence.nextN(monthly(8), at(2026, 11, 10), 3)
        assertEquals(
            listOf(at(2026, 12, 8, 7, 30), at(2027, 1, 8, 7, 30), at(2027, 2, 8, 7, 30)),
            result
        )
    }
}
