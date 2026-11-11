package com.anqi.app.ui

import com.anqi.app.model.Alarm
import com.anqi.app.model.RepeatType
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WEEKDAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

fun formatTime(hour: Int, minute: Int): String =
    String.format(Locale.US, "%02d:%02d", hour, minute)

fun weekdayName(isoDay: Int): String =
    WEEKDAYS.getOrElse(isoDay - 1) { "" }

fun formatDate(date: LocalDate): String =
    "${date.monthValue}月${date.dayOfMonth}日 ${weekdayName(date.dayOfWeek.value)}"

fun formatDateLong(date: LocalDate): String =
    "${date.year}年${date.monthValue}月${date.dayOfMonth}日 ${weekdayName(date.dayOfWeek.value)}"

fun formatMonthDay(month: Int, day: Int): String = "${month}月${day}日"

fun relativeDay(target: LocalDate, today: LocalDate = LocalDate.now()): String = when {
    target == today -> "今天"
    target == today.plusDays(1) -> "明天"
    target == today.plusDays(2) -> "后天"
    else -> "${target.monthValue}月${target.dayOfMonth}日"
}

fun countdownText(target: ZonedDateTime, now: ZonedDateTime = ZonedDateTime.now()): String {
    val duration = Duration.between(now, target)
    val minutes = duration.toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "就是现在"
        minutes < 60 -> "还有 $minutes 分钟"
        minutes < 24 * 60 -> "还有 ${minutes / 60} 小时 ${minutes % 60} 分钟"
        else -> "还有 ${duration.toDays()} 天"
    }
}

fun nextOccurrenceTimeText(next: ZonedDateTime?): String =
    if (next == null) "--:--" else formatTime(next.hour, next.minute)

fun repeatSummary(alarm: Alarm): String = when (alarm.repeatType) {
    RepeatType.ONCE -> {
        val date = alarm.date?.let(::formatDate) ?: "未选择日期"
        "单次 · $date"
    }
    RepeatType.DAILY -> "每天"
    RepeatType.WORKDAYS -> "工作日 · 周一至周五"
    RepeatType.WEEKLY -> {
        val days = alarm.weekdays.sorted().joinToString("、") { weekdayName(it) }
        if (days.isBlank()) "每周 · 未选择" else "每周 · $days"
    }
    RepeatType.MONTHLY_DAY -> "每月 ${alarm.monthlyDay ?: 1} 日"
    RepeatType.MONTHLY_LAST_DAY -> "每月最后一天"
    RepeatType.MONTHLY_NTH_WEEKDAY -> {
        val nth = when (alarm.nth) {
            -1 -> "最后一个"
            1 -> "第一个"
            2 -> "第二个"
            3 -> "第三个"
            4 -> "第四个"
            else -> "第 ${alarm.nth ?: 1} 个"
        }
        "每月$nth${weekdayName(alarm.nthWeekday ?: 1)}"
    }
    RepeatType.YEARLY -> "每年 ${alarm.yearlyMonth ?: 1} 月 ${alarm.yearlyDay ?: 1} 日"
}

fun endSummary(alarm: Alarm): String = when {
    alarm.remainingCount != null -> "共 ${alarm.remainingCount} 次"
    alarm.endDate != null -> "至 ${formatDateLong(alarm.endDate)}"
    else -> "永不结束"
}

fun scheduleSummary(alarm: Alarm): String = buildString {
    append(repeatSummary(alarm))
    if (alarm.repeatType != RepeatType.ONCE) {
        append(" · ")
        append(endSummary(alarm))
    }
}

fun nextPreviewLabel(next: ZonedDateTime, index: Int): String {
    val prefix = when (index) {
        0 -> "下一次"
        1 -> "再下一次"
        else -> "第 ${index + 1} 次"
    }
    return "$prefix · ${formatDate(next.toLocalDate())} ${formatTime(next.hour, next.minute)}"
}

fun timeUntil(target: ZonedDateTime, now: ZonedDateTime = ZonedDateTime.now()): String {
    val minutes = Duration.between(now, target).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 60 -> "${minutes} 分钟后"
        minutes < 24 * 60 -> "${minutes / 60} 小时后"
        else -> "${minutes / (24 * 60)} 天后"
    }
}

fun nowDateTimeText(): String =
    LocalDateTime.now().format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))

/** A past instant as "11月8日 21:04", for the "上次检查" row. */
fun formatStamp(epochMillis: Long): String =
    java.time.Instant.ofEpochMilli(epochMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA))
