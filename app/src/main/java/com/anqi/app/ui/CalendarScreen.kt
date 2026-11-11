package com.anqi.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.testTag
import com.anqi.app.data.CalendarOccurrence
import com.anqi.app.data.CalendarProjector
import com.anqi.app.data.NextOccurrence
import com.anqi.app.model.Alarm
import com.anqi.app.ui.theme.Anqi
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.temporal.WeekFields

enum class CalendarTab(val label: String) {
    ALL("所有"),
    YEAR("年"),
    MONTH("月"),
    WEEK("周")
}

/**
 * The four main tabs. ALL deliberately reuses the original 1.1.x home screen;
 * the three calendar tabs only read the same alarms through CalendarProjector.
 */
@Composable
fun MainTabs(
    modifier: Modifier,
    alarms: List<Alarm>,
    now: ZonedDateTime,
    setupHint: SetupHint?,
    onOpenSetup: () -> Unit,
    tab: CalendarTab,
    focusDate: LocalDate,
    monthAnchor: YearMonth,
    yearAnchor: Int,
    weekAnchor: LocalDate,
    compact: Boolean,
    selecting: Boolean,
    selectedIds: Set<Long>,
    onTab: (CalendarTab) -> Unit,
    onAdd: () -> Unit,
    onNewOnDate: (LocalDate) -> Unit,
    onOpen: (Alarm) -> Unit,
    onToggle: (Alarm, Boolean) -> Unit,
    onLongPress: (Alarm) -> Unit,
    onToggleSelection: (Alarm) -> Unit,
    onExitSelection: () -> Unit,
    onRequestDelete: () -> Unit,
    onSelectAll: () -> Unit,
    onOpenSettings: () -> Unit,
    onYearChange: (Int) -> Unit,
    onOpenMonth: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onWeekChange: (LocalDate) -> Unit,
    onToday: () -> Unit,
    swipeFailed: Boolean = false,
    swipeLate: Boolean = false,
    missedCount: Int = 0,
    onOpenAutostart: () -> Unit = {}
) {
    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                CalendarTab.ALL -> HomePane(
                    modifier = Modifier.fillMaxSize(),
                    alarms = alarms,
                    upcoming = upcoming(alarms, now),
                    now = now,
                    setupHint = setupHint,
                    onOpenSetup = onOpenSetup,
                    compact = compact,
                    selecting = selecting,
                    selectedIds = selectedIds,
                    onAdd = onAdd,
                    onOpen = onOpen,
                    onToggle = onToggle,
                    onLongPress = onLongPress,
                    onToggleSelection = onToggleSelection,
                    onExitSelection = onExitSelection,
                    onRequestDelete = onRequestDelete,
                    onSelectAll = onSelectAll,
                    onOpenSettings = onOpenSettings,
                    swipeFailed = swipeFailed,
                    swipeLate = swipeLate,
                    missedCount = missedCount,
                    onOpenAutostart = onOpenAutostart,
                    navigationPadding = false
                )

                CalendarTab.YEAR -> YearPane(
                    modifier = Modifier.fillMaxSize(),
                    year = yearAnchor,
                    focusDate = focusDate,
                    today = now.toLocalDate(),
                    alarms = alarms,
                    now = now,
                    onPrev = { onYearChange(yearAnchor - 1) },
                    onNext = { onYearChange(yearAnchor + 1) },
                    onToday = onToday,
                    onOpenMonth = onOpenMonth
                )

                CalendarTab.MONTH -> MonthPane(
                    modifier = Modifier.fillMaxSize(),
                    month = monthAnchor,
                    focusDate = focusDate,
                    today = now.toLocalDate(),
                    alarms = alarms,
                    now = now,
                    onPrev = { onMonthChange(monthAnchor.minusMonths(1)) },
                    onNext = { onMonthChange(monthAnchor.plusMonths(1)) },
                    onToday = onToday,
                    onSelectDate = onSelectDate,
                    onNewOnDate = onNewOnDate,
                    onOpen = onOpen
                )

                CalendarTab.WEEK -> WeekPane(
                    modifier = Modifier.fillMaxSize(),
                    weekStart = weekAnchor,
                    focusDate = focusDate,
                    today = now.toLocalDate(),
                    alarms = alarms,
                    now = now,
                    onPrev = { onWeekChange(weekAnchor.minusWeeks(1)) },
                    onNext = { onWeekChange(weekAnchor.plusWeeks(1)) },
                    onToday = onToday,
                    onSelectDate = onSelectDate,
                    onNewOnDate = onNewOnDate,
                    onOpen = onOpen
                )
            }
        }
        AnqiBottomBar(tab = tab, onTab = onTab)
    }
}

private fun upcoming(alarms: List<Alarm>, now: ZonedDateTime): List<Pair<Alarm, ZonedDateTime>> =
    alarms.asSequence()
        .filter { it.enabled }
        .mapNotNull { alarm -> NextOccurrence.next(alarm, now)?.let { alarm to it } }
        .sortedBy { it.second }
        .take(4)
        .toList()

@Composable
private fun AnqiBottomBar(tab: CalendarTab, onTab: (CalendarTab) -> Unit) {
    Surface(color = Anqi.palette.canvas) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Hairline()
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                CalendarTab.entries.forEach { item ->
                    val active = item == tab
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (active) Anqi.palette.signalSoft else Color.Transparent)
                            .clickable { onTab(item) }
                            .semantics {
                                selected = active
                                role = Role.Tab
                                contentDescription = item.label
                            }
                            .testTag("tab-${item.name.lowercase()}")
                            .padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TabGlyph(item, if (active) Anqi.palette.signal else Anqi.palette.muted)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.3.sp),
                            color = if (active) Anqi.palette.signal else Anqi.palette.muted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabGlyph(tab: CalendarTab, color: Color) {
    Canvas(Modifier.size(21.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()
        when (tab) {
            CalendarTab.ALL -> {
                repeat(3) { i ->
                    val y = h * (0.24f + i * 0.26f)
                    drawCircle(color, radius = 1.5.dp.toPx(), center = Offset(2.5.dp.toPx(), y))
                    drawLine(color, Offset(7.dp.toPx(), y), Offset(w * 0.9f, y), stroke)
                }
            }

            CalendarTab.YEAR -> {
                repeat(4) { row ->
                    repeat(3) { col ->
                        drawCircle(
                            color,
                            radius = 1.8.dp.toPx(),
                            center = Offset(w * (0.26f + col * 0.24f), h * (0.24f + row * 0.18f))
                        )
                    }
                }
            }

            CalendarTab.MONTH -> {
                val r = 4.dp.toPx()
                drawRoundRect(
                    color = color,
                    topLeft = Offset(2.dp.toPx(), 3.dp.toPx()),
                    size = Size(w - 4.dp.toPx(), h - 5.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                    style = Stroke(stroke)
                )
                drawLine(color, Offset(2.dp.toPx(), 7.dp.toPx()), Offset(w - 2.dp.toPx(), 7.dp.toPx()), stroke)
                repeat(3) { i ->
                    drawCircle(color, 1.2.dp.toPx(), Offset(w * (0.32f + i * 0.18f), h * 0.6f))
                }
            }

            CalendarTab.WEEK -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(2.dp.toPx(), 3.dp.toPx()),
                    size = Size(w - 4.dp.toPx(), h - 5.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    style = Stroke(stroke)
                )
                repeat(7) { i ->
                    val x = 3.dp.toPx() + (w - 6.dp.toPx()) * i / 6f
                    drawLine(color, Offset(x, 6.dp.toPx()), Offset(x, h - 3.dp.toPx()), 1.dp.toPx())
                }
                drawLine(color, Offset(2.dp.toPx(), 7.dp.toPx()), Offset(w - 2.dp.toPx(), 7.dp.toPx()), stroke)
            }
        }
    }
}

@Composable
private fun PaneHeader(
    title: String,
    subtitle: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 14.dp, top = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                modifier = Modifier.testTag("pane-title"),
                style = MaterialTheme.typography.headlineMedium,
                color = Anqi.palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            SectionLabel(subtitle)
        }
        ChevronButton(left = true, onClick = onPrev)
        AnqiTextButton("今天", onClick = onToday, color = Anqi.palette.muted)
        ChevronButton(left = false, onClick = onNext)
    }
}

@Composable
private fun ChevronButton(left: Boolean, onClick: () -> Unit) {
    val ink = Anqi.palette.ink
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (left) "上一步" else "下一步" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(10.dp)) {
            val stroke = 1.7.dp.toPx()
            val direction = if (left) -1f else 1f
            drawLine(
                ink,
                Offset(size.width * (0.5f - direction * 0.2f), size.height * 0.15f),
                Offset(size.width * (0.5f + direction * 0.2f), size.height * 0.5f),
                stroke
            )
            drawLine(
                ink,
                Offset(size.width * (0.5f + direction * 0.2f), size.height * 0.5f),
                Offset(size.width * (0.5f - direction * 0.2f), size.height * 0.85f),
                stroke
            )
        }
    }
}

@Composable
private fun YearPane(
    modifier: Modifier,
    year: Int,
    focusDate: LocalDate,
    today: LocalDate,
    alarms: List<Alarm>,
    now: ZonedDateTime,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onOpenMonth: (LocalDate) -> Unit
) {
    val occurrences = remember(alarms, year, today) {
        CalendarProjector.occurrences(
            alarms = alarms,
            from = LocalDate.of(year, 1, 1),
            until = LocalDate.of(year, 12, 31),
            now = now
        )
    }
    val counts = remember(occurrences) {
        occurrences.groupingBy { it.date }.eachCount()
    }

    Column(modifier.statusBarsPadding()) {
        PaneHeader(
            title = year.toString(),
            subtitle = "全年安排 · ${counts.size} 天有提醒",
            onPrev = onPrev,
            onNext = onNext,
            onToday = onToday
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(6.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = if (maxWidth >= 600.dp) 3 else 2
                Column(Modifier.fillMaxWidth()) {
                    (1..12).chunked(columns).forEach { months ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            months.forEach { monthValue ->
                                MiniMonth(
                                    month = YearMonth.of(year, monthValue),
                                    focusDate = focusDate,
                                    today = today,
                                    counts = counts,
                                    onOpenMonth = onOpenMonth,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(columns - months.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun MiniMonth(
    month: YearMonth,
    focusDate: LocalDate,
    today: LocalDate,
    counts: Map<LocalDate, Int>,
    onOpenMonth: (LocalDate) -> Unit,
    modifier: Modifier
) {
    Column(modifier) {
        Text(
            text = "${month.monthValue}月",
            modifier = Modifier.clickable { onOpenMonth(month.atDay(1)) },
            style = MaterialTheme.typography.titleMedium,
            color = if (YearMonth.from(focusDate) == month) Anqi.palette.signal else Anqi.palette.ink
        )
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 0.sp),
                    color = Anqi.palette.muted,
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        gridCells(month).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(
                        Modifier.weight(1f).height(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (date != null) {
                            val count = counts[date] ?: 0
                            val selected = date == focusDate
                            val isToday = date == today
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) Anqi.palette.signal else Color.Transparent)
                                    .then(
                                        if (isToday && !selected) {
                                            Modifier.border(1.dp, Anqi.palette.signal, CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { onOpenMonth(date) },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        fontSize = 9.sp,
                                        lineHeight = 10.sp,
                                        color = if (selected) Anqi.palette.onSignal else Anqi.palette.ink
                                    )
                                    if (count > 0) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                            repeat(minOf(count, 3)) {
                                                Box(
                                                    Modifier
                                                        .size(2.dp)
                                                        .background(
                                                            if (selected) Anqi.palette.onSignal else Anqi.palette.signal,
                                                            CircleShape
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthPane(
    modifier: Modifier,
    month: YearMonth,
    focusDate: LocalDate,
    today: LocalDate,
    alarms: List<Alarm>,
    now: ZonedDateTime,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onNewOnDate: (LocalDate) -> Unit,
    onOpen: (Alarm) -> Unit
) {
    val first = month.atDay(1)
    val last = month.atEndOfMonth()
    val occurrences = remember(alarms, month, today) {
        CalendarProjector.occurrences(alarms, first, last, now)
    }
    val byDay = remember(occurrences) { occurrences.groupBy { it.date } }
    val dayItems = byDay[focusDate].orEmpty()

    Column(modifier.statusBarsPadding()) {
        PaneHeader(
            title = "${month.monthValue}月",
            subtitle = "${month.year}年 · ${byDay.size} 天有提醒",
            onPrev = onPrev,
            onNext = onNext,
            onToday = onToday
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Surface(
                color = Anqi.palette.surface,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
                    WeekdayHeader()
                    Spacer(Modifier.height(4.dp))
                    MonthGrid(
                        month = month,
                        selectedDate = focusDate,
                        today = today,
                        byDay = byDay,
                        cellHeight = 46.dp,
                        onSelectDate = onSelectDate
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            DayAgendaCard(
                date = focusDate,
                occurrences = dayItems,
                today = today,
                onOpen = onOpen,
                onNewOnDate = onNewOnDate
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(Modifier.fillMaxWidth()) {
        listOf("一", "二", "三", "四", "五", "六", "日").forEach { day ->
            Text(
                text = day,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                color = Anqi.palette.muted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    byDay: Map<LocalDate, List<CalendarOccurrence>>,
    cellHeight: Dp,
    onSelectDate: (LocalDate) -> Unit
) {
    gridCells(month).chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            week.forEach { date ->
                Box(
                    Modifier.weight(1f).height(cellHeight),
                    contentAlignment = Alignment.Center
                ) {
                    if (date != null) {
                        val selected = date == selectedDate
                        val count = byDay[date]?.size ?: 0
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (selected) Anqi.palette.signal else Color.Transparent)
                                .then(
                                    if (date == today && !selected) {
                                        Modifier.border(1.dp, Anqi.palette.signal, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { onSelectDate(date) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (selected || date == today) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = if (selected) Anqi.palette.onSignal else Anqi.palette.ink
                                )
                                if (count > 0) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        repeat(minOf(count, 3)) {
                                            Box(
                                                Modifier
                                                    .size(3.dp)
                                                    .background(
                                                        if (selected) Anqi.palette.onSignal else Anqi.palette.signal,
                                                        CircleShape
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayAgendaCard(
    date: LocalDate,
    occurrences: List<CalendarOccurrence>,
    today: LocalDate,
    onOpen: (Alarm) -> Unit,
    onNewOnDate: (LocalDate) -> Unit
) {
    val past = date.isBefore(today)
    Surface(
        color = Anqi.palette.surface,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = formatDateLong(date),
                        style = MaterialTheme.typography.titleMedium,
                        color = Anqi.palette.ink
                    )
                    Text(
                        text = when {
                            past -> "过去的日期"
                            occurrences.isEmpty() -> "这天没有安排"
                            else -> "${occurrences.size} 个提醒"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (occurrences.isEmpty()) Anqi.palette.muted else Anqi.palette.signal
                    )
                }
                if (date == today) {
                    SignalDot()
                }
            }
            if (occurrences.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                occurrences.forEach { occurrence ->
                    AgendaRow(occurrence = occurrence, onOpen = onOpen)
                }
            }
            if (!past) {
                Spacer(Modifier.height(8.dp))
                AnqiTextButton(
                    text = if (occurrences.isEmpty()) "在这一天新建" else "再加一个",
                    onClick = { onNewOnDate(date) },
                    color = Anqi.palette.signal
                )
            }
        }
    }
}

@Composable
private fun AgendaRow(occurrence: CalendarOccurrence, onOpen: (Alarm) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(occurrence.alarm) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatTime(occurrence.at.hour, occurrence.at.minute),
            modifier = Modifier.width(58.dp),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
            color = Anqi.palette.ink
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = occurrence.alarm.label.ifBlank { "提醒" },
                style = MaterialTheme.typography.bodyLarge,
                color = Anqi.palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = repeatSummary(occurrence.alarm),
                style = MaterialTheme.typography.labelMedium,
                color = Anqi.palette.muted
            )
        }
        Text("›", fontSize = 20.sp, color = Anqi.palette.muted)
    }
    Hairline()
}

@Composable
private fun WeekPane(
    modifier: Modifier,
    weekStart: LocalDate,
    focusDate: LocalDate,
    today: LocalDate,
    alarms: List<Alarm>,
    now: ZonedDateTime,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onNewOnDate: (LocalDate) -> Unit,
    onOpen: (Alarm) -> Unit
) {
    val weekEnd = weekStart.plusDays(6)
    val dates = remember(weekStart) { (0L..6L).map { weekStart.plusDays(it) } }
    val byDay = remember(alarms, weekStart, today) {
        CalendarProjector.byDay(alarms, weekStart, weekEnd, now)
    }
    var dialogDate by remember { mutableStateOf<LocalDate?>(null) }
    val weekNumber = weekStart.get(WeekFields.ISO.weekOfWeekBasedYear())

    Column(modifier.statusBarsPadding()) {
        PaneHeader(
            title = weekTitle(weekStart, weekEnd),
            subtitle = "第 $weekNumber 周",
            onPrev = onPrev,
            onNext = onNext,
            onToday = onToday
        )
        WeekHeader(
            dates = dates,
            selectedDate = focusDate,
            today = today,
            onSelectDate = { date ->
                onSelectDate(date)
                dialogDate = date
            }
        )
        WeekGrid(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            weekStart = weekStart,
            dates = dates,
            today = today,
            now = now,
            byDay = byDay,
            onOpen = onOpen,
            onOpenDay = { dialogDate = it }
        )
    }

    val selectedDialogDate = dialogDate
    if (selectedDialogDate != null) {
        DayAgendaDialog(
            date = selectedDialogDate,
            occurrences = byDay[selectedDialogDate].orEmpty(),
            today = today,
            onDismiss = { dialogDate = null },
            onOpen = {
                dialogDate = null
                onOpen(it)
            },
            onNewOnDate = {
                dialogDate = null
                onNewOnDate(it)
            }
        )
    }
}

private fun weekTitle(start: LocalDate, end: LocalDate): String =
    if (start.month == end.month) {
        "${start.monthValue}月${start.dayOfMonth}日 – ${end.dayOfMonth}日"
    } else {
        "${start.monthValue}月${start.dayOfMonth}日 – ${end.monthValue}月${end.dayOfMonth}日"
    }

@Composable
private fun WeekHeader(
    dates: List<LocalDate>,
    selectedDate: LocalDate,
    today: LocalDate,
    onSelectDate: (LocalDate) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp)
    ) {
        Spacer(Modifier.width(36.dp))
        dates.forEach { date ->
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectDate(date) }
                    .padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = weekdayName(date.dayOfWeek.value).removePrefix("周"),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                    color = if (date == today) Anqi.palette.signal else Anqi.palette.muted
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        date == today || date == selectedDate -> Anqi.palette.signal
                        else -> Anqi.palette.ink
                    }
                )
            }
        }
    }
}

@Composable
private fun WeekGrid(
    modifier: Modifier,
    weekStart: LocalDate,
    dates: List<LocalDate>,
    today: LocalDate,
    now: ZonedDateTime,
    byDay: Map<LocalDate, List<CalendarOccurrence>>,
    onOpen: (Alarm) -> Unit,
    onOpenDay: (LocalDate) -> Unit
) {
    val hourHeight = 44.dp
    val gutter = 36.dp
    val totalHeight = hourHeight * 24
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val gridLine = Anqi.palette.line.copy(alpha = 0.7f)
    val signal = Anqi.palette.signal

    BoxWithConstraints(modifier) {
        val dayWidth = (maxWidth - gutter) / 7
        val viewportHeight = maxHeight
        LaunchedEffect(weekStart, scroll.maxValue, viewportHeight) {
            if (scroll.maxValue > 0) {
                val target = with(density) {
                    val nowY = hourHeight * ((now.hour + now.minute / 60f))
                    (nowY - viewportHeight / 2f).roundToPx()
                }
                scroll.scrollTo(target.coerceIn(0, scroll.maxValue))
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Box(Modifier.fillMaxWidth().height(totalHeight)) {
                Canvas(Modifier.fillMaxSize()) {
                    val line = gridLine
                    val x0 = gutter.toPx()
                    for (hour in 0..24) {
                        val y = hourHeight.toPx() * hour
                        drawLine(line, Offset(0f, y), Offset(size.width, y), 0.7.dp.toPx())
                    }
                    for (day in 0..7) {
                        val x = x0 + dayWidth.toPx() * day
                        drawLine(line, Offset(x, 0f), Offset(x, size.height), 0.7.dp.toPx())
                    }
                    if (today in dates) {
                        val day = java.time.temporal.ChronoUnit.DAYS.between(weekStart, today).toInt()
                        val minutes = now.hour * 60 + now.minute + now.second / 60f
                        val y = minutes / 60f * hourHeight.toPx()
                        val x = x0 + dayWidth.toPx() * day
                        drawLine(
                            signal.copy(alpha = 0.45f),
                            Offset(x0, y),
                            Offset(size.width, y),
                            1.dp.toPx()
                        )
                        drawCircle(
                            signal,
                            radius = 2.5.dp.toPx(),
                            center = Offset(x + dayWidth.toPx() / 2f, y)
                        )
                    }
                }

                Column(Modifier.width(gutter)) {
                    repeat(24) { hour ->
                        Box(
                            Modifier.fillMaxWidth().height(hourHeight),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            Text(
                                text = String.format(java.util.Locale.US, "%02d", hour),
                                modifier = Modifier.padding(top = 1.dp, end = 5.dp),
                                fontSize = 9.sp,
                                color = Anqi.palette.muted
                            )
                        }
                    }
                }

                val grouped = byDay.values.flatten()
                    .groupBy { it.date to (it.at.hour * 60 + it.at.minute) }
                grouped.forEach { (key, items) ->
                    val date = key.first
                    val day = java.time.temporal.ChronoUnit.DAYS.between(weekStart, date).toInt()
                    if (day !in 0..6) return@forEach
                    val minutes = key.second
                    val y = (hourHeight * (minutes / 60f)).coerceAtMost(totalHeight - 46.dp)
                    Box(
                        Modifier
                            .offset(x = gutter + dayWidth * day + 1.dp, y = y + 1.dp)
                            .width(dayWidth - 2.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Anqi.palette.signalSoft)
                            .border(0.7.dp, Anqi.palette.signal.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
                            .clickable {
                                if (items.size == 1) onOpen(items.first().alarm) else onOpenDay(date)
                            }
                            .padding(horizontal = 3.dp, vertical = 3.dp)
                    ) {
                        Column {
                            Text(
                                text = if (items.size > 1) {
                                    "${formatTime(items.first().at.hour, items.first().at.minute)} · ${items.size}"
                                } else {
                                    formatTime(items.first().at.hour, items.first().at.minute)
                                },
                                fontSize = 9.sp,
                                lineHeight = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                color = Anqi.palette.signal,
                                maxLines = 1,
                                overflow = TextOverflow.Clip
                            )
                            if (items.size == 1 && dayWidth >= 40.dp) {
                                Text(
                                    text = items.first().alarm.label.ifBlank { "提醒" },
                                    fontSize = 9.sp,
                                    lineHeight = 11.sp,
                                    color = Anqi.palette.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayAgendaDialog(
    date: LocalDate,
    occurrences: List<CalendarOccurrence>,
    today: LocalDate,
    onDismiss: () -> Unit,
    onOpen: (Alarm) -> Unit,
    onNewOnDate: (LocalDate) -> Unit
) {
    val past = date.isBefore(today)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(20.dp).widthIn(max = 420.dp),
            color = Anqi.palette.surface,
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = formatDateLong(date),
                            style = MaterialTheme.typography.titleMedium,
                            color = Anqi.palette.ink
                        )
                        Text(
                            text = if (occurrences.isEmpty()) "这天没有安排" else "${occurrences.size} 个提醒",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Anqi.palette.muted
                        )
                    }
                    AnqiTextButton("关闭", onDismiss, color = Anqi.palette.muted)
                }
                if (occurrences.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        occurrences.forEach { occurrence ->
                            AgendaRow(occurrence = occurrence, onOpen = onOpen)
                        }
                    }
                }
                if (!past) {
                    Spacer(Modifier.height(14.dp))
                    AnqiButton(
                        text = "在这一天新建",
                        onClick = { onNewOnDate(date) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun gridCells(month: YearMonth): List<LocalDate?> {
    val offset = month.atDay(1).dayOfWeek.value - 1
    val cells = mutableListOf<LocalDate?>()
    repeat(offset) { cells += null }
    (1..month.lengthOfMonth()).forEach { cells += month.atDay(it) }
    while (cells.size < 42) cells += null
    return cells
}
