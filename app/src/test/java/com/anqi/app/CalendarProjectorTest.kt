package com.anqi.app

import com.anqi.app.data.CalendarProjector
import com.anqi.app.model.Alarm
import com.anqi.app.model.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class CalendarProjectorTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = ZonedDateTime.of(2026, 11, 10, 14, 0, 0, 0, zone)

    @Test
    fun monthlyProjectionOnlyUsesMatchingDay() {
        val alarm = Alarm(
            id = 1,
            label = "交电费",
            repeatType = RepeatType.MONTHLY_DAY,
            monthlyDay = 8,
            hour = 8
        )
        val result = CalendarProjector.occurrences(
            listOf(alarm),
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 12, 31),
            now
        )
        assertEquals(listOf(LocalDate.of(2026, 12, 8)), result.map { it.date })
        assertTrue(result.all { it.at.hour == 8 && it.alarm.id == 1L })
    }

    @Test
    fun pastOccurrencesAreNotProjected() {
        val alarm = Alarm(
            repeatType = RepeatType.DAILY,
            hour = 8
        )
        val result = CalendarProjector.occurrences(
            listOf(alarm),
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 10),
            now
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun disabledAlarmIsExcluded() {
        val alarm = Alarm(repeatType = RepeatType.DAILY, hour = 8, enabled = false)
        val result = CalendarProjector.occurrences(
            listOf(alarm),
            LocalDate.of(2026, 11, 10),
            LocalDate.of(2026, 11, 20),
            now
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun endDateAndRemainingCountAreRespected() {
        val alarm = Alarm(
            repeatType = RepeatType.DAILY,
            hour = 20,
            endDate = LocalDate.of(2026, 11, 12),
            remainingCount = 2
        )
        val result = CalendarProjector.occurrences(
            listOf(alarm),
            LocalDate.of(2026, 11, 10),
            LocalDate.of(2026, 11, 20),
            now
        )
        assertEquals(2, result.size)
        assertEquals(LocalDate.of(2026, 11, 10), result.first().date)
    }

    @Test
    fun weeklyAndWorkdayRulesStayOnTheirDays() {
        val weekly = Alarm(
            id = 1,
            repeatType = RepeatType.WEEKLY,
            weekdays = setOf(1, 4),
            hour = 9
        )
        val workday = Alarm(
            id = 2,
            repeatType = RepeatType.WORKDAYS,
            hour = 10
        )
        val result = CalendarProjector.occurrences(
            listOf(weekly, workday),
            LocalDate.of(2026, 11, 9),
            LocalDate.of(2026, 11, 15),
            now
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 11, 11) to 2L,
                LocalDate.of(2026, 11, 12) to 1L,
                LocalDate.of(2026, 11, 12) to 2L,
                LocalDate.of(2026, 11, 13) to 2L
            ),
            result.map { it.date to it.alarm.id }
        )
    }

    @Test
    fun oneTimeAlarmAppearsOnce() {
        val alarm = Alarm(
            repeatType = RepeatType.ONCE,
            date = LocalDate.of(2026, 11, 18),
            hour = 7,
            minute = 30
        )
        val result = CalendarProjector.occurrences(
            listOf(alarm),
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 12, 31),
            now
        )
        assertEquals(1, result.size)
        assertEquals(LocalDate.of(2026, 11, 18), result.single().date)
    }

    @Test
    fun weekStartsOnMonday() {
        assertEquals(
            LocalDate.of(2026, 11, 9),
            CalendarProjector.weekStart(LocalDate.of(2026, 11, 15))
        )
    }
}
