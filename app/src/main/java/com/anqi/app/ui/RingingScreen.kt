package com.anqi.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anqi.app.data.NextOccurrence
import com.anqi.app.model.Alarm
import com.anqi.app.ui.theme.Anqi
import kotlinx.coroutines.delay
import java.time.ZonedDateTime

/**
 * Full-screen ringing face. It uses the same palette as the rest of the app
 * (light or dark, following the system) plus the same monospace clock and single
 * red accent, so an alarm looks like this app rather than a system dialog — and
 * never as an unexplained black screen in light mode.
 */
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
    val palette = Anqi.palette

    val pulse = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Box(
        Modifier
            .fillMaxSize()
            .testTag("ringing-screen")
            .background(
                Brush.verticalGradient(
                    listOf(palette.raised, palette.canvas, palette.canvas)
                )
            )
            .drawBehind {
                // Soft accent glow behind the clock, so the panel has depth instead
                // of reading as a flat, unlit screen.
                val center = Offset(size.width / 2f, size.height * 0.42f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            palette.signal.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = size.width * 0.85f
                    ),
                    radius = size.width * 0.85f,
                    center = center
                )
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 26.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .alpha(dotAlpha)
                        .background(palette.signal, CircleShape)
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    "月度闹钟",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.ink
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "正在响铃",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.signal
                )
            }

            Spacer(Modifier.weight(0.36f))

            Surface(
                color = palette.surface,
                shape = RoundedCornerShape(32.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 22.dp, vertical = 26.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "现在",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.signal
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            formatDateLong(now.toLocalDate()),
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.muted
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    TimeDisplay(
                        hour = alarm.hour,
                        minute = alarm.minute,
                        fontSize = 104,
                        color = palette.ink
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = alarm.label.ifBlank { "月度闹钟提醒" },
                        style = MaterialTheme.typography.headlineMedium,
                        color = palette.ink
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = repeatSummary(alarm),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.muted
                    )
                }
            }

            if (alarm.note.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    color = palette.raised,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = alarm.note,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.muted
                    )
                }
            }

            Spacer(Modifier.weight(0.55f))

            if (next != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "下一次 · ${formatDate(next.toLocalDate())} ${formatTime(next.hour, next.minute)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.muted
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            AnqiButton(
                text = "贪睡 $snoozeMinutes 分钟",
                onClick = onSnooze,
                modifier = Modifier.fillMaxWidth(),
                filled = false
            )
            Spacer(Modifier.height(11.dp))
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
                    .padding(top = 14.dp, bottom = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted,
                textAlign = TextAlign.Center
            )
        }
    }
}
