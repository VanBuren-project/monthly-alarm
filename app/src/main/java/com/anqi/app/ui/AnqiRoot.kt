package com.anqi.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anqi.app.data.CalendarProjector
import com.anqi.app.data.NextOccurrence
import com.anqi.app.model.Alarm
import com.anqi.app.ui.theme.Anqi
import com.anqi.app.viewmodel.AlarmViewModel
import com.anqi.app.viewmodel.SystemReadiness
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime

@Composable
fun AnqiRoot(
    viewModel: AlarmViewModel,
    onRequestNotifications: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var creating by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    var tab by remember { mutableStateOf(CalendarTab.ALL) }
    var focusDate by remember { mutableStateOf(LocalDate.now()) }
    var monthAnchor by remember { mutableStateOf(YearMonth.now()) }
    var yearAnchor by remember { mutableIntStateOf(LocalDate.now().year) }
    var weekAnchor by remember { mutableStateOf(CalendarProjector.weekStart(LocalDate.now())) }
    var newSeed by remember { mutableStateOf<Alarm?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = ZonedDateTime.now()
        }
    }

    val alarms = viewModel.alarms.toList()

    val detailVisible = editing != null || creating || showSettings
    val exitSelection: () -> Unit = {
        selecting = false
        selectedIds = emptySet()
        confirmDelete = false
    }
    val enterSelection: (Alarm) -> Unit = { alarm ->
        selecting = true
        selectedIds = setOf(alarm.id)
    }
    val toggleSelection: (Alarm) -> Unit = { alarm ->
        selectedIds = if (alarm.id in selectedIds) {
            selectedIds - alarm.id
        } else {
            selectedIds + alarm.id
        }
    }
    val selectAll: () -> Unit = {
        selectedIds = if (selectedIds.size == alarms.size) {
            emptySet()
        } else {
            alarms.map { it.id }.toSet()
        }
    }
    val openEditor: (Alarm) -> Unit = {
        exitSelection()
        creating = false
        showSettings = false
        newSeed = null
        editing = it
    }
    val openNew: () -> Unit = {
        exitSelection()
        editing = null
        showSettings = false
        newSeed = newAlarmDraft()
        creating = true
    }
    val openNewOn: (LocalDate) -> Unit = { date ->
        exitSelection()
        editing = null
        showSettings = false
        newSeed = newAlarmDraft(date)
        creating = true
    }
    val closeDetail: () -> Unit = {
        editing = null
        creating = false
        showSettings = false
        newSeed = null
    }
    val selectTab: (CalendarTab) -> Unit = { next ->
        exitSelection()
        when (next) {
            CalendarTab.MONTH -> monthAnchor = YearMonth.from(focusDate)
            CalendarTab.WEEK -> weekAnchor = CalendarProjector.weekStart(focusDate)
            CalendarTab.YEAR -> yearAnchor = focusDate.year
            CalendarTab.ALL -> Unit
        }
        tab = next
    }
    val changeMonth: (YearMonth) -> Unit = { next ->
        monthAnchor = next
        if (YearMonth.from(focusDate) != next) {
            focusDate = if (next == YearMonth.now()) LocalDate.now() else next.atDay(1)
        }
    }
    val openMonth: (LocalDate) -> Unit = { date ->
        focusDate = date
        monthAnchor = YearMonth.from(date)
        tab = CalendarTab.MONTH
    }
    val changeYear: (Int) -> Unit = { nextYear ->
        yearAnchor = nextYear
        if (focusDate.year != nextYear) {
            val month = monthAnchor.monthValue
            monthAnchor = YearMonth.of(nextYear, month)
            focusDate = monthAnchor.atDay(1)
        }
    }
    val changeWeek: (LocalDate) -> Unit = { next ->
        weekAnchor = next
        if (focusDate !in next..next.plusDays(6)) {
            focusDate = if (next == CalendarProjector.weekStart(LocalDate.now())) {
                LocalDate.now()
            } else {
                next
            }
        }
    }
    val goToday: () -> Unit = {
        val today = LocalDate.now()
        focusDate = today
        monthAnchor = YearMonth.from(today)
        yearAnchor = today.year
        weekAnchor = CalendarProjector.weekStart(today)
    }
    BackHandler(enabled = selecting, onBack = exitSelection)
    BackHandler(enabled = detailVisible, onBack = closeDetail)
    val openSettings: () -> Unit = {
        exitSelection()
        editing = null
        creating = false
        newSeed = null
        showSettings = true
    }

    val mainTabs: @Composable (Modifier, Boolean) -> Unit = { mainModifier, isCompact ->
        MainTabs(
            modifier = mainModifier,
            alarms = alarms,
            now = now,
            readiness = viewModel.readiness,
            tab = tab,
            focusDate = focusDate,
            monthAnchor = monthAnchor,
            yearAnchor = yearAnchor,
            weekAnchor = weekAnchor,
            compact = isCompact,
            selecting = selecting,
            selectedIds = selectedIds,
            onTab = selectTab,
            onAdd = openNew,
            onNewOnDate = openNewOn,
            onOpen = openEditor,
            onToggle = viewModel::setEnabled,
            onLongPress = enterSelection,
            onToggleSelection = toggleSelection,
            onExitSelection = exitSelection,
            onRequestDelete = { confirmDelete = true },
            onSelectAll = selectAll,
            onOpenSettings = openSettings,
            onYearChange = changeYear,
            onOpenMonth = openMonth,
            onMonthChange = changeMonth,
            onSelectDate = { focusDate = it },
            onWeekChange = changeWeek,
            onToday = goToday,
            onRequestNotifications = onRequestNotifications,
            onOpenExactAlarmSettings = onOpenExactAlarmSettings,
            onOpenFullScreenSettings = onOpenFullScreenSettings
        )
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Anqi.palette.canvas)
    ) {
        val expanded = maxWidth >= 840.dp

        if (expanded && detailVisible) {
            Row(Modifier.fillMaxSize()) {
                mainTabs(
                    Modifier.weight(1f).fillMaxHeight(),
                    true
                )
                Box(
                    Modifier
                        .width(0.7.dp)
                        .fillMaxHeight()
                        .background(Anqi.palette.line)
                )
                Box(Modifier.width(420.dp).fillMaxHeight()) {
                    DetailPane(
                        editing = editing,
                        creating = creating,
                        seed = newSeed,
                        settings = showSettings,
                        viewModel = viewModel,
                        onBack = closeDetail,
                        onRequestNotifications = onRequestNotifications,
                        onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                        onOpenFullScreenSettings = onOpenFullScreenSettings,
                        onOpenAppSettings = onOpenAppSettings,
                        onOpenBatterySettings = onOpenBatterySettings
                    )
                }
            }
        } else if (detailVisible) {
            DetailPane(
                editing = editing,
                creating = creating,
                seed = newSeed,
                settings = showSettings,
                viewModel = viewModel,
                onBack = closeDetail,
                onRequestNotifications = onRequestNotifications,
                onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                onOpenFullScreenSettings = onOpenFullScreenSettings,
                onOpenAppSettings = onOpenAppSettings,
                onOpenBatterySettings = onOpenBatterySettings
            )
        } else {
            mainTabs(Modifier.fillMaxSize(), false)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = Anqi.palette.surface,
            title = {
                Text(
                    text = "删除 ${selectedIds.size} 个提醒？",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Anqi.palette.ink
                )
            },
            text = {
                Text(
                    text = "删除后无法恢复，正在进行中的提醒也会被取消。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Anqi.palette.muted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedIds.forEach(viewModel::delete)
                        exitSelection()
                    }
                ) {
                    Text("删除", color = Anqi.palette.signal)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("取消", color = Anqi.palette.muted)
                }
            }
        )
    }
}

@Composable
private fun DetailPane(
    editing: Alarm?,
    creating: Boolean,
    seed: Alarm?,
    settings: Boolean,
    viewModel: AlarmViewModel,
    onBack: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    if (settings) {
        SettingsScreen(
            viewModel = viewModel,
            onBack = onBack,
            onRequestNotifications = onRequestNotifications,
            onOpenExactAlarmSettings = onOpenExactAlarmSettings,
            onOpenFullScreenSettings = onOpenFullScreenSettings,
            onOpenAppSettings = onOpenAppSettings,
            onOpenBatterySettings = onOpenBatterySettings
        )
    } else {
        // Keep the new-alarm draft stable while the host recomposes (for example
        // when the clock ticks); otherwise every tick would reset the editor.
        val initial = remember(editing, creating, seed) { editing ?: seed ?: newAlarmDraft() }
        AlarmEditorScreen(
            initial = initial,
            isNew = creating,
            onBack = onBack,
            onSave = { viewModel.save(it); onBack() },
            onDelete = {
                if (!creating) viewModel.delete(initial.id)
                onBack()
            }
        )
    }
}

@Composable
fun HomePane(
    modifier: Modifier,
    alarms: List<Alarm>,
    upcoming: List<Pair<Alarm, ZonedDateTime>>,
    now: ZonedDateTime,
    readiness: SystemReadiness,
    compact: Boolean,
    selecting: Boolean,
    selectedIds: Set<Long>,
    onAdd: () -> Unit,
    onOpen: (Alarm) -> Unit,
    onToggle: (Alarm, Boolean) -> Unit,
    onLongPress: (Alarm) -> Unit,
    onToggleSelection: (Alarm) -> Unit,
    onExitSelection: () -> Unit,
    onRequestDelete: () -> Unit,
    onSelectAll: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenSettings: () -> Unit,
    navigationPadding: Boolean = true
) {
    val next = upcoming.firstOrNull()
    val horizontal = if (compact) 22.dp else 24.dp

    Column(
        modifier
            .statusBarsPadding()
            .then(if (navigationPadding) Modifier.navigationBarsPadding() else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            Modifier
                .weight(1f)
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontal)
        ) {
            if (selecting) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnqiTextButton("退出", onClick = onExitSelection, color = Anqi.palette.muted)
                    Text(
                        text = "已选择 ${selectedIds.size} 项",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = Anqi.palette.ink,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    AnqiTextButton(
                        text = if (selectedIds.size == alarms.size) "取消全选" else "全选",
                        onClick = onSelectAll,
                        color = Anqi.palette.signal
                    )
                }
                Spacer(Modifier.height(26.dp))
                SectionLabel("选择提醒")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "轻点切换选择，长按也可以继续选择。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Anqi.palette.muted
                )
                Spacer(Modifier.height(14.dp))
                alarms.forEach { alarm ->
                    AlarmRow(
                        alarm = alarm,
                        selectionMode = true,
                        selected = alarm.id in selectedIds,
                        onOpen = { onOpen(alarm) },
                        onToggle = { onToggle(alarm, it) },
                        onLongPress = { onLongPress(alarm) },
                        onSelect = { onToggleSelection(alarm) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(24.dp))
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .background(Anqi.palette.signal, RoundedCornerShape(3.dp))
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "月度闹钟",
                        style = MaterialTheme.typography.titleLarge,
                        color = Anqi.palette.ink
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatDate(LocalDate.now()),
                        style = MaterialTheme.typography.labelMedium,
                        color = Anqi.palette.muted
                    )
                }

                Spacer(Modifier.height(24.dp))
                Surface(
                    color = Anqi.palette.surface,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
                        SectionLabel("下一次")
                        Spacer(Modifier.height(8.dp))
                        if (next != null) {
                            val (alarm, nextTime) = next
                            TimeDisplay(
                                hour = nextTime.hour,
                                minute = nextTime.minute,
                                modifier = Modifier.fillMaxWidth(),
                                fontSize = if (compact) 58 else 72
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = formatDateLong(nextTime.toLocalDate()),
                                style = MaterialTheme.typography.titleMedium,
                                color = Anqi.palette.ink
                            )
                            Spacer(Modifier.height(5.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = countdownText(nextTime, now),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Anqi.palette.signal
                                )
                                if (alarm.label.isNotBlank()) {
                                    Text(
                                        text = "  ·  ${alarm.label}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Anqi.palette.muted
                                    )
                                }
                            }
                        } else {
                            Text(
                                "还没有下一次",
                                style = MaterialTheme.typography.headlineLarge,
                                color = Anqi.palette.ink
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "新建一个提醒，把容易忘记的日子交给它。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Anqi.palette.muted
                            )
                        }
                    }
                }

                if (!readiness.allGood) {
                    Spacer(Modifier.height(16.dp))
                    ReadinessBanner(
                        readiness = readiness,
                        onRequestNotifications = onRequestNotifications,
                        onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                        onOpenFullScreenSettings = onOpenFullScreenSettings
                    )
                }

                if (upcoming.size > 1) {
                    Spacer(Modifier.height(30.dp))
                    SectionLabel("接下来")
                    Spacer(Modifier.height(10.dp))
                    upcoming.drop(1).forEach { (alarm, time) ->
                        UpcomingRow(alarm = alarm, time = time, now = now)
                        Spacer(Modifier.height(10.dp))
                    }
                }

                Spacer(Modifier.height(30.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("所有闹钟", Modifier.weight(1f))
                    AnqiTextButton("设置", onClick = onOpenSettings, color = Anqi.palette.muted)
                }
                Spacer(Modifier.height(10.dp))

                if (alarms.isEmpty()) {
                    EmptyHome()
                } else {
                    alarms.forEach { alarm ->
                        AlarmRow(
                            alarm = alarm,
                            selectionMode = false,
                            selected = false,
                            onOpen = { onOpen(alarm) },
                            onToggle = { onToggle(alarm, it) },
                            onLongPress = { onLongPress(alarm) },
                            onSelect = {}
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        Surface(color = Anqi.palette.canvas) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontal, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                if (selecting) {
                    AnqiButton(
                        text = if (selectedIds.isEmpty()) {
                            "选择要删除的提醒"
                        } else {
                            "删除 ${selectedIds.size} 个提醒"
                        },
                        onClick = onRequestDelete,
                        enabled = selectedIds.isNotEmpty(),
                        modifier = Modifier
                            .widthIn(max = 760.dp)
                            .fillMaxWidth()
                    )
                } else {
                    AnqiButton(
                        text = if (alarms.isEmpty()) "创建第一个提醒" else "新建提醒",
                        onClick = onAdd,
                        modifier = Modifier
                            .widthIn(max = 760.dp)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun UpcomingRow(alarm: Alarm, time: ZonedDateTime, now: ZonedDateTime) {
    Surface(
        color = Anqi.palette.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTime(time.hour, time.minute),
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                color = Anqi.palette.ink
            )
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = alarm.label.ifBlank { repeatShort(alarm) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Anqi.palette.ink
                )
                Text(
                    text = "${relativeDay(time.toLocalDate())} · ${countdownText(time, now)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Anqi.palette.muted
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmRow(
    alarm: Alarm,
    selectionMode: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onLongPress: () -> Unit,
    onSelect: () -> Unit
) {
    val background by animateColorAsState(
        targetValue = if (selected) Anqi.palette.signalSoft else Anqi.palette.surface,
        label = "alarmRowBackground"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (!selectionMode && !alarm.enabled) 0.48f else 1f)
            .clip(RoundedCornerShape(22.dp))
            .background(background)
            .combinedClickable(
                onClick = { if (selectionMode) onSelect() else onOpen() },
                onLongClick = if (selectionMode) null else onLongPress
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatTime(alarm.hour, alarm.minute),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    fontSize = 25.sp,
                    color = Anqi.palette.ink
                )
                if (alarm.label.isNotBlank()) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = alarm.label,
                        modifier = Modifier.padding(bottom = 3.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Anqi.palette.ink,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = repeatSummary(alarm),
                style = MaterialTheme.typography.labelMedium,
                color = Anqi.palette.muted
            )
        }
        if (selectionMode) {
            Box(
                Modifier
                    .size(24.dp)
                    .background(
                        if (selected) Anqi.palette.signal else Color.Transparent,
                        CircleShape
                    )
                    .border(
                        1.4.dp,
                        if (selected) Anqi.palette.signal else Anqi.palette.line,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(Anqi.palette.onSignal, CircleShape)
                    )
                }
            }
        } else {
            Switch(
                checked = alarm.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Anqi.palette.onSignal,
                    checkedTrackColor = Anqi.palette.signal,
                    uncheckedThumbColor = Anqi.palette.muted,
                    uncheckedTrackColor = Color.Transparent,
                    uncheckedBorderColor = Anqi.palette.line
                )
            )
        }
    }
}

@Composable
private fun ReadinessBanner(
    readiness: SystemReadiness,
    onRequestNotifications: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenSettings: () -> Unit
) {
    val missing = when {
        !readiness.notifications -> "通知权限未开启"
        !readiness.exactAlarms -> "精确闹钟权限未开启"
        else -> "全屏提醒权限未开启"
    }
    val action = when {
        !readiness.notifications -> onRequestNotifications
        !readiness.exactAlarms -> onOpenExactAlarmSettings
        else -> onOpenFullScreenSettings
    }
    Surface(
        color = Anqi.palette.signalSoft,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "还差一步",
                    style = MaterialTheme.typography.titleMedium,
                    color = Anqi.palette.ink
                )
                Text(
                    missing,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Anqi.palette.muted
                )
            }
            AnqiTextButton("去开启", onClick = action, color = Anqi.palette.signal)
        }
    }
}

@Composable
private fun EmptyHome() {
    Surface(
        color = Anqi.palette.surface,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "把重复的日子记下来一次，之后交给它。",
                style = MaterialTheme.typography.bodyLarge,
                color = Anqi.palette.ink
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "交电费、房租、还款、体检、纪念日。可以精确到某一天，也可以每月、每年、工作日重复。",
                style = MaterialTheme.typography.bodyMedium,
                color = Anqi.palette.muted
            )
        }
    }
}

@Composable
private fun EmptyDetail() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "月度闹钟",
                style = MaterialTheme.typography.headlineLarge,
                color = Anqi.palette.ink
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "在左侧选择一个提醒，或新建一个。",
                style = MaterialTheme.typography.bodyMedium,
                color = Anqi.palette.muted
            )
        }
    }
}

private fun repeatShort(alarm: Alarm): String = when (alarm.repeatType) {
    com.anqi.app.model.RepeatType.ONCE -> "单次提醒"
    com.anqi.app.model.RepeatType.DAILY -> "每天"
    com.anqi.app.model.RepeatType.WORKDAYS -> "工作日"
    com.anqi.app.model.RepeatType.WEEKLY -> repeatSummary(alarm)
    com.anqi.app.model.RepeatType.MONTHLY_DAY -> repeatSummary(alarm)
    com.anqi.app.model.RepeatType.MONTHLY_LAST_DAY -> repeatSummary(alarm)
    com.anqi.app.model.RepeatType.MONTHLY_NTH_WEEKDAY -> repeatSummary(alarm)
    com.anqi.app.model.RepeatType.YEARLY -> repeatSummary(alarm)
}
