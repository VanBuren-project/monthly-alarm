package com.anqi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anqi.app.data.NextOccurrence
import com.anqi.app.model.Alarm
import com.anqi.app.model.RepeatType
import com.anqi.app.ui.theme.Anqi
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime

fun newAlarmDraft(date: LocalDate? = null): Alarm {
    val now = ZonedDateTime.now()
    val target = date ?: now.toLocalDate().plusDays(1)
    // A brand new reminder defaults to the current wall-clock time. If the
    // user picked today, that minute has usually already started, so move to
    // the next minute instead of creating an immediately expired draft.
    val time = if (target == now.toLocalDate()) now.plusMinutes(1) else now
    return Alarm(
        hour = time.hour,
        minute = time.minute,
        repeatType = RepeatType.ONCE,
        date = target
    )
}

@Composable
fun AlarmEditorScreen(
    initial: Alarm,
    isNew: Boolean,
    onBack: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: () -> Unit
) {
    var draft by remember(initial.id, initial.createdAt, isNew) { mutableStateOf(initial) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showRepeatSheet by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    // The preview shows what *would* happen, so a paused reminder is evaluated
    // as if it were switched on — otherwise editing it displays "no next
    // occurrence", which reads like the rule itself is broken.
    val previews = NextOccurrence.nextN(draft.copy(enabled = true), count = 3)

    Column(
        Modifier
            .fillMaxSize()
            .background(Anqi.palette.canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnqiTextButton("返回", onBack, color = Anqi.palette.muted)
            Text(
                text = if (isNew) "新建提醒" else "编辑提醒",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = Anqi.palette.ink,
                textAlign = TextAlign.Center
            )
            AnqiTextButton(
                "保存",
                onClick = {
                    val check = checkBeforeSave(draft)
                    validationError = check.error
                    if (check.error == null) onSave(check.alarm)
                },
                color = Anqi.palette.signal
            )
        }
        Spacer(Modifier.height(6.dp))

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(Modifier.height(28.dp))
            validationError?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Anqi.palette.signal
                )
                Spacer(Modifier.height(16.dp))
            }
            SectionLabel("时间")
            Spacer(Modifier.height(10.dp))
            Surface(
                color = Anqi.palette.surface,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier
                        .clickable { showTimePicker = true }
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    TimeDisplay(
                        hour = draft.hour,
                        minute = draft.minute,
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 72
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "点击修改时间",
                        style = MaterialTheme.typography.labelMedium,
                        color = Anqi.palette.muted
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            AnqiTextField(
                value = draft.label,
                onValueChange = { draft = draft.copy(label = it.take(40)) },
                placeholder = "给这个日子一个名字，例如：交电费"
            )

            Spacer(Modifier.height(14.dp))
            EditorRow(
                label = "重复",
                value = repeatSummary(draft),
                onClick = { showRepeatSheet = true }
            )
            EditorRow(
                label = "结束",
                value = endSummary(draft),
                onClick = { showRepeatSheet = true }
            )

            Spacer(Modifier.height(8.dp))
            AnqiTextField(
                value = draft.note,
                onValueChange = { draft = draft.copy(note = it.take(160)) },
                placeholder = "备注（可选）",
                minHeight = 68
            )

            Spacer(Modifier.height(24.dp))
            SectionLabel("提醒方式")
            Spacer(Modifier.height(10.dp))
            Surface(
                color = Anqi.palette.surface,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "震动",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Anqi.palette.ink
                            )
                            Text(
                                "响铃时同时震动",
                                style = MaterialTheme.typography.labelMedium,
                                color = Anqi.palette.muted
                            )
                        }
                        Switch(
                            checked = draft.vibrate,
                            onCheckedChange = { draft = draft.copy(vibrate = it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Anqi.palette.onSignal,
                                checkedTrackColor = Anqi.palette.signal,
                                uncheckedThumbColor = Anqi.palette.muted,
                                uncheckedTrackColor = Color.Transparent,
                                uncheckedBorderColor = Anqi.palette.line
                            )
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "声音",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Anqi.palette.ink
                        )
                        Text(
                            "系统默认闹钟",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Anqi.palette.muted
                        )
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            SectionLabel("接下来三次")
            Spacer(Modifier.height(10.dp))
            if (previews.isEmpty()) {
                Text(
                    "这个规则暂时没有下一次，请检查日期。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Anqi.palette.signal
                )
            } else {
                Surface(
                    color = Anqi.palette.surface,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        previews.forEachIndexed { index, time ->
                            PreviewRow(index = index, time = time)
                        }
                    }
                }
            }

            if (!isNew) {
                Spacer(Modifier.height(34.dp))
                AnqiTextButton(
                    "删除这个提醒",
                    onClick = { showDelete = true },
                    color = Anqi.palette.signal,
                    modifier = Modifier.fillMaxWidth()
                )
            }
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    if (showTimePicker) {
        AnqiTimePickerDialog(
            initialHour = draft.hour,
            initialMinute = draft.minute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                draft = draft.copy(hour = hour, minute = minute)
                validationError = null
                showTimePicker = false
            }
        )
    }

    if (showRepeatSheet) {
        RepeatSheetDialog(
            alarm = draft,
            onChange = {
                draft = it
                validationError = null
            },
            onDismiss = { showRepeatSheet = false }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = Anqi.palette.surface,
            titleContentColor = Anqi.palette.ink,
            textContentColor = Anqi.palette.muted,
            title = { Text("删除这个提醒？", style = MaterialTheme.typography.titleLarge) },
            text = { Text("删除后不会再响铃，这个操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = { showDelete = false; onDelete() }) {
                    Text("删除", color = Anqi.palette.signal)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text("保留", color = Anqi.palette.ink)
                }
            }
        )
    }
}

@Composable
private fun PreviewRow(index: Int, time: ZonedDateTime) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when (index) {
                0 -> "第一次"
                1 -> "第二次"
                else -> "第三次"
            },
            modifier = Modifier.width(58.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Anqi.palette.muted
        )
        Text(
            text = "${formatDate(time.toLocalDate())} ${formatTime(time.hour, time.minute)}",
            style = MaterialTheme.typography.bodyMedium,
            color = Anqi.palette.ink
        )
    }
}

@Composable
private fun EditorRow(label: String, value: String, onClick: () -> Unit) {
    Surface(
        color = Anqi.palette.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(
            Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                modifier = Modifier.width(64.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Anqi.palette.muted
            )
            Text(
                value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = Anqi.palette.ink
            )
            Text(
                "›",
                style = MaterialTheme.typography.titleLarge,
                color = Anqi.palette.muted
            )
        }
    }
}

@Composable
fun AnqiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minHeight: Int = 44
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Anqi.palette.ink),
        cursorBrush = SolidColor(Anqi.palette.signal),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Anqi.palette.surface)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isBlank()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Anqi.palette.muted
                    )
                }
                inner()
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepeatSheetDialog(
    alarm: Alarm,
    onChange: (Alarm) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Anqi.palette.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 3.dp)
                    .background(Anqi.palette.line, RoundedCornerShape(2.dp))
            )
        }
    ) {
        RepeatSheetContent(alarm = alarm, onChange = onChange, onDone = onDismiss)
    }
}

@Composable
private fun RepeatSheetContent(
    alarm: Alarm,
    onChange: (Alarm) -> Unit,
    onDone: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var pickerForEnd by remember { mutableStateOf(false) }
    val previews = NextOccurrence.nextN(alarm, count = 3)

    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "重复",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                color = Anqi.palette.ink
            )
            AnqiTextButton("完成", onDone, color = Anqi.palette.signal)
        }
        Spacer(Modifier.height(18.dp))

        val types = RepeatType.entries
        types.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { type ->
                    ChoicePill(
                        text = repeatTypeLabel(type),
                        selected = alarm.repeatType == type,
                        onClick = { onChange(alarm.withType(type)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(22.dp))

        when (alarm.repeatType) {
            RepeatType.ONCE -> {
                EditorRow(
                    label = "日期",
                    value = alarm.date?.let(::formatDateLong) ?: "选择日期",
                    onClick = {
                        pickerForEnd = false
                        showDatePicker = true
                    }
                )
                Text(
                    "只在这个日期提醒一次。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Anqi.palette.muted
                )
            }
            RepeatType.DAILY -> {
                Text(
                    "每天在设定时间提醒。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Anqi.palette.muted
                )
            }
            RepeatType.WORKDAYS -> {
                Text(
                    "周一至周五提醒，周末不提醒。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Anqi.palette.muted
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "中国法定节假日与调休将在后续版本接入。",
                    style = MaterialTheme.typography.labelMedium,
                    color = Anqi.palette.muted
                )
            }
            RepeatType.WEEKLY -> {
                SectionLabel("选择星期")
                Spacer(Modifier.height(10.dp))
                WeekdayChips(
                    selected = alarm.weekdays,
                    onToggle = { day ->
                        val next = if (day in alarm.weekdays) alarm.weekdays - day else alarm.weekdays + day
                        onChange(alarm.copy(weekdays = next))
                    }
                )
            }
            RepeatType.MONTHLY_DAY -> {
                SectionLabel("每月几号")
                Spacer(Modifier.height(10.dp))
                NumberGrid(
                    values = (1..31).toList(),
                    selected = alarm.monthlyDay,
                    columns = 7,
                    onSelect = { onChange(alarm.copy(monthlyDay = it)) }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "没有这个日期的月份会自动跳过。",
                    style = MaterialTheme.typography.labelMedium,
                    color = Anqi.palette.muted
                )
            }
            RepeatType.MONTHLY_LAST_DAY -> {
                Text(
                    "每个月的最后一天提醒。二月的最后一天会自动适配闰年。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Anqi.palette.muted
                )
            }
            RepeatType.MONTHLY_NTH_WEEKDAY -> {
                SectionLabel("第几个")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "第一个", 2 to "第二个", 3 to "第三个", 4 to "第四个", -1 to "最后一个")
                        .forEach { (value, label) ->
                            ChoicePill(
                                text = label,
                                selected = alarm.nth == value,
                                onClick = { onChange(alarm.copy(nth = value)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                }
                Spacer(Modifier.height(16.dp))
                SectionLabel("星期几")
                Spacer(Modifier.height(10.dp))
                WeekdayChips(
                    selected = setOfNotNull(alarm.nthWeekday),
                    onToggle = { onChange(alarm.copy(nthWeekday = it)) }
                )
            }
            RepeatType.YEARLY -> {
                SectionLabel("月份")
                Spacer(Modifier.height(10.dp))
                NumberGrid(
                    values = (1..12).toList(),
                    selected = alarm.yearlyMonth,
                    columns = 6,
                    suffix = "月",
                    onSelect = { onChange(alarm.copy(yearlyMonth = it)) }
                )
                Spacer(Modifier.height(16.dp))
                SectionLabel("日期")
                Spacer(Modifier.height(10.dp))
                NumberGrid(
                    values = (1..31).toList(),
                    selected = alarm.yearlyDay,
                    columns = 7,
                    onSelect = { onChange(alarm.copy(yearlyDay = it)) }
                )
                val valid = isValidYearly(alarm.yearlyMonth, alarm.yearlyDay)
                if (!valid) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "这个日期不存在，请调整为有效日期。",
                        style = MaterialTheme.typography.labelMedium,
                        color = Anqi.palette.signal
                    )
                }
            }
        }

        Spacer(Modifier.height(26.dp))
        SectionLabel("结束")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoicePill(
                text = "永不",
                selected = alarm.endDate == null && alarm.remainingCount == null,
                onClick = {
                    onChange(alarm.copy(endDate = null, remainingCount = null))
                },
                modifier = Modifier.weight(1f)
            )
            ChoicePill(
                text = "指定日期",
                selected = alarm.endDate != null,
                onClick = {
                    onChange(
                        alarm.copy(
                            endDate = alarm.endDate ?: LocalDate.now().plusYears(1),
                            remainingCount = null
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            )
            ChoicePill(
                text = "次数",
                selected = alarm.remainingCount != null,
                onClick = {
                    onChange(
                        alarm.copy(
                            endDate = null,
                            remainingCount = alarm.remainingCount ?: 10
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }

        if (alarm.endDate != null) {
            Spacer(Modifier.height(10.dp))
            EditorRow(
                label = "结束日期",
                value = formatDateLong(alarm.endDate),
                onClick = {
                    pickerForEnd = true
                    showDatePicker = true
                }
            )
        }
        if (alarm.remainingCount != null) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "剩余次数",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Anqi.palette.ink
                )
                AnqiTextButton("−", {
                    onChange(alarm.copy(remainingCount = ((alarm.remainingCount ?: 1) - 1).coerceAtLeast(1)))
                }, color = Anqi.palette.muted)
                Text(
                    "${alarm.remainingCount}",
                    modifier = Modifier.width(38.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = Anqi.palette.ink,
                    textAlign = TextAlign.Center
                )
                AnqiTextButton("+", {
                    onChange(alarm.copy(remainingCount = ((alarm.remainingCount ?: 1) + 1).coerceAtMost(365)))
                }, color = Anqi.palette.muted)
            }
        }

        Spacer(Modifier.height(26.dp))
        SectionLabel("接下来三次")
        Spacer(Modifier.height(6.dp))
        if (previews.isEmpty()) {
            Text(
                "这个规则暂时没有下一次。",
                style = MaterialTheme.typography.bodyMedium,
                color = Anqi.palette.signal
            )
        } else {
            previews.forEachIndexed { index, time ->
                PreviewRow(index = index, time = time)
            }
        }
        Spacer(Modifier.height(28.dp))
    }

    if (showDatePicker) {
        AnqiCalendarDialog(
            initial = if (pickerForEnd) {
                alarm.endDate ?: LocalDate.now()
            } else {
                alarm.date ?: LocalDate.now()
            },
            minDate = if (pickerForEnd) LocalDate.now() else LocalDate.now(),
            onDismiss = { showDatePicker = false },
            onSelect = { date ->
                if (pickerForEnd) {
                    onChange(alarm.copy(endDate = date))
                } else {
                    onChange(alarm.copy(date = date))
                }
                showDatePicker = false
            }
        )
    }
}

@Composable
private fun repeatTypeLabel(type: RepeatType): String = when (type) {
    RepeatType.ONCE -> "单次"
    RepeatType.DAILY -> "每天"
    RepeatType.WORKDAYS -> "工作日"
    RepeatType.WEEKLY -> "每周"
    RepeatType.MONTHLY_DAY -> "每月某日"
    RepeatType.MONTHLY_LAST_DAY -> "每月最后一天"
    RepeatType.MONTHLY_NTH_WEEKDAY -> "每月第几个"
    RepeatType.YEARLY -> "每年某日"
}

private fun Alarm.withType(type: RepeatType): Alarm {
    val base = copy(
        repeatType = type,
        date = null,
        weekdays = emptySet(),
        monthlyDay = null,
        nth = null,
        nthWeekday = null,
        yearlyMonth = null,
        yearlyDay = null
    )
    val today = LocalDate.now()
    return when (type) {
        RepeatType.ONCE -> base.copy(date = this.date ?: today.plusDays(1))
        RepeatType.DAILY -> base
        RepeatType.WORKDAYS -> base
        RepeatType.WEEKLY -> base.copy(weekdays = setOf(today.dayOfWeek.value))
        RepeatType.MONTHLY_DAY -> base.copy(monthlyDay = today.dayOfMonth)
        RepeatType.MONTHLY_LAST_DAY -> base
        RepeatType.MONTHLY_NTH_WEEKDAY -> base.copy(nth = 1, nthWeekday = today.dayOfWeek.value)
        RepeatType.YEARLY -> base.copy(yearlyMonth = today.monthValue, yearlyDay = today.dayOfMonth)
    }
}

/**
 * Validates a draft and returns the alarm to persist.
 *
 * Saving also switches the reminder on. A paused reminder whose time is being
 * changed is meant to ring at that new time — that is the whole point of editing
 * it. Validating the draft as-is is a guaranteed failure instead, because
 * `NextOccurrence.next()` returns null for a disabled alarm, so a perfectly
 * valid future time would be rejected with "这个时间已经过去". That mismatch
 * between the preview (evaluated as enabled) and the validation (evaluated as
 * disabled) was the whole bug.
 */
internal fun checkBeforeSave(draft: Alarm): SaveCheck {
    val candidate = normalize(draft).copy(enabled = true)
    val error = when {
        candidate.repeatType == RepeatType.YEARLY &&
            !isValidYearly(candidate.yearlyMonth, candidate.yearlyDay) ->
            "这个日期不存在，请先调整月份或日期。"
        NextOccurrence.next(candidate) == null ->
            "这个时间已经过去，或结束条件已用完。请调整日期或时间。"
        else -> null
    }
    return SaveCheck(candidate, error)
}

internal data class SaveCheck(val alarm: Alarm, val error: String?)

fun normalize(alarm: Alarm): Alarm {
    val today = LocalDate.now()
    val base = alarm.copy(
        label = alarm.label.trim(),
        note = alarm.note.trim()
    )
    return when (alarm.repeatType) {
        RepeatType.ONCE -> base.copy(
            date = alarm.date ?: today.plusDays(1),
            weekdays = emptySet(),
            monthlyDay = null,
            nth = null,
            nthWeekday = null,
            yearlyMonth = null,
            yearlyDay = null
        )
        RepeatType.DAILY -> base.copy(
            date = null, weekdays = emptySet(), monthlyDay = null,
            nth = null, nthWeekday = null, yearlyMonth = null, yearlyDay = null
        )
        RepeatType.WORKDAYS -> base.copy(
            date = null, weekdays = emptySet(), monthlyDay = null,
            nth = null, nthWeekday = null, yearlyMonth = null, yearlyDay = null
        )
        RepeatType.WEEKLY -> base.copy(
            date = null,
            weekdays = if (alarm.weekdays.isEmpty()) setOf(today.dayOfWeek.value) else alarm.weekdays,
            monthlyDay = null, nth = null, nthWeekday = null, yearlyMonth = null, yearlyDay = null
        )
        RepeatType.MONTHLY_DAY -> base.copy(
            date = null, weekdays = emptySet(),
            monthlyDay = (alarm.monthlyDay ?: today.dayOfMonth).coerceIn(1, 31),
            nth = null, nthWeekday = null, yearlyMonth = null, yearlyDay = null
        )
        RepeatType.MONTHLY_LAST_DAY -> base.copy(
            date = null, weekdays = emptySet(), monthlyDay = null,
            nth = null, nthWeekday = null, yearlyMonth = null, yearlyDay = null
        )
        RepeatType.MONTHLY_NTH_WEEKDAY -> base.copy(
            date = null, weekdays = emptySet(), monthlyDay = null,
            nth = alarm.nth ?: 1,
            nthWeekday = alarm.nthWeekday ?: today.dayOfWeek.value
        )
        RepeatType.YEARLY -> base.copy(
            date = null, weekdays = emptySet(), monthlyDay = null,
            nth = null, nthWeekday = null,
            yearlyMonth = (alarm.yearlyMonth ?: today.monthValue).coerceIn(1, 12),
            yearlyDay = (alarm.yearlyDay ?: today.dayOfMonth).coerceIn(1, 31)
        )
    }
}

private fun isValidYearly(month: Int?, day: Int?): Boolean {
    if (month == null || day == null) return true
    return runCatching { YearMonth.of(2024, month).atDay(day) }.isSuccess
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnqiTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val state: TimePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = Anqi.palette.surface
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "选择时间",
                    style = MaterialTheme.typography.titleLarge,
                    color = Anqi.palette.ink
                )
                Spacer(Modifier.height(18.dp))
                TimeInput(
                    state = state,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    AnqiTextButton("取消", onDismiss, color = Anqi.palette.muted)
                    Spacer(Modifier.width(14.dp))
                    AnqiTextButton(
                        "确定",
                        { onConfirm(state.hour, state.minute) },
                        color = Anqi.palette.signal
                    )
                }
            }
        }
    }
}

@Composable
fun AnqiCalendarDialog(
    initial: LocalDate,
    minDate: LocalDate? = null,
    onDismiss: () -> Unit,
    onSelect: (LocalDate) -> Unit
) {
    var month by remember { mutableStateOf(YearMonth.from(initial)) }
    val today = LocalDate.now()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = Anqi.palette.surface
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnqiTextButton("‹", { month = month.minusMonths(1) }, color = Anqi.palette.muted)
                    Text(
                        "${month.year}年${month.monthValue}月",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = Anqi.palette.ink,
                        textAlign = TextAlign.Center
                    )
                    AnqiTextButton("›", { month = month.plusMonths(1) }, color = Anqi.palette.muted)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { day ->
                        Text(
                            day,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = Anqi.palette.muted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                val offset = month.atDay(1).dayOfWeek.value - 1
                val cells = List(offset) { null } + (1..month.lengthOfMonth()).toList()
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            if (day == null) {
                                Spacer(Modifier.weight(1f).height(38.dp))
                            } else {
                                val date = month.atDay(day)
                                val enabled = minDate == null || !date.isBefore(minDate)
                                val selected = date == initial
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .padding(2.dp)
                                        .then(
                                            if (selected) {
                                                Modifier.background(Anqi.palette.signal, CircleShape)
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .then(
                                            if (date == today && !selected) {
                                                Modifier.border(0.8.dp, Anqi.palette.signal, CircleShape)
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clickable(enabled = enabled) { onSelect(date) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "$day",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = when {
                                            selected -> Anqi.palette.onSignal
                                            !enabled -> Anqi.palette.muted
                                            date == today -> Anqi.palette.signal
                                            else -> Anqi.palette.ink
                                        }
                                    )
                                }
                            }
                        }
                        repeat(7 - week.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    AnqiTextButton("取消", onDismiss, color = Anqi.palette.muted)
                }
            }
        }
    }
}
