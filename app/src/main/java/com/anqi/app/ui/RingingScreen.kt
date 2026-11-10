package com.anqi.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anqi.app.data.NextOccurrence
import com.anqi.app.model.Alarm
import com.anqi.app.ui.theme.Anqi
import kotlinx.coroutines.delay
import java.time.ZonedDateTime

@Composable
fun RingingScreen(
    alarm: Alarm,
    snoozeMinutes: Int,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(enabled = true) { }
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = ZonedDateTime.now()
        }
    }
    val next = remember(alarm.id) { NextOccurrence.next(alarm, now) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Anqi.palette.canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(Anqi.palette.signal, CircleShape)
            )
            Spacer(Modifier.width(9.dp))
            Text(
                "月度闹钟",
                style = MaterialTheme.typography.titleLarge,
                color = Anqi.palette.ink
            )
            Spacer(Modifier.weight(1f))
            Text(
                "正在响铃",
                style = MaterialTheme.typography.labelMedium,
                color = Anqi.palette.signal
            )
        }

        Spacer(Modifier.weight(0.45f))
        Text(
            text = formatDateLong(now.toLocalDate()),
            style = MaterialTheme.typography.bodyMedium,
            color = Anqi.palette.muted
        )
        Spacer(Modifier.height(10.dp))
        TimeDisplay(
            hour = alarm.hour,
            minute = alarm.minute,
            fontSize = 96,
            color = Anqi.palette.ink
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = alarm.label.ifBlank { "月度闹钟提醒" },
            style = MaterialTheme.typography.headlineMedium,
            color = Anqi.palette.ink
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = repeatSummary(alarm),
            style = MaterialTheme.typography.bodyMedium,
            color = Anqi.palette.muted
        )
        if (alarm.note.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = alarm.note,
                style = MaterialTheme.typography.bodyMedium,
                color = Anqi.palette.muted
            )
        }
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier
                .width(48.dp)
                .height(1.dp)
                .background(Anqi.palette.signal)
        )
        Spacer(Modifier.weight(0.75f))

        if (next != null) {
            Text(
                text = "下一次 · ${formatDate(next.toLocalDate())} ${formatTime(next.hour, next.minute)}",
                style = MaterialTheme.typography.labelMedium,
                color = Anqi.palette.muted
            )
            Spacer(Modifier.height(12.dp))
        }

        AnqiButton(
            text = "贪睡 $snoozeMinutes 分钟",
            onClick = onSnooze,
            modifier = Modifier.fillMaxWidth(),
            filled = false
        )
        Spacer(Modifier.height(10.dp))
        AnqiButton(
            text = "停止",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            filled = true
        )
        Text(
            text = "停止后，重复提醒会自动安排下一次。音量键也可以贪睡。",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Anqi.palette.muted
        )
    }
}
