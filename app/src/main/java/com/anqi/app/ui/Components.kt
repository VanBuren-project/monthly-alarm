package com.anqi.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anqi.app.ui.theme.Anqi

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Anqi.palette.muted) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = color
    )
}

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Anqi.palette.line) {
    Box(
        modifier
            .fillMaxWidth()
            .height(0.7.dp)
            .background(color)
    )
}

@Composable
fun SignalDot(modifier: Modifier = Modifier, color: Color = Anqi.palette.signal) {
    Box(
        modifier
            .size(6.dp)
            .background(color, CircleShape)
    )
}

@Composable
fun TimeDisplay(
    hour: Int,
    minute: Int,
    modifier: Modifier = Modifier,
    fontSize: Int = 76,
    color: Color = Anqi.palette.ink
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(modifier) {
        // Keep the hero time on one line on 320dp screens and at 200% font size.
        val widthFit = (maxWidth.value / 3.25f).coerceAtLeast(28f)
        val fitted = minOf(fontSize.toFloat(), widthFit) / fontScale
        Text(
            text = formatTime(hour, minute),
            color = color,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = fitted.sp,
            letterSpacing = (-fitted * 0.05f).sp,
            lineHeight = (fitted * 1.02f).sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun AnqiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(26.dp),
        color = if (filled) Anqi.palette.signal else Color.Transparent,
        contentColor = if (filled) Anqi.palette.onSignal else Anqi.palette.ink,
        border = if (filled) null else BorderStroke(0.8.dp, Anqi.palette.line)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun AnqiTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Anqi.palette.ink
) {
    Text(
        text = text,
        modifier = modifier
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 44.dp)
            .wrapContentHeight(Alignment.CenterVertically)
            .padding(horizontal = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = color
    )
}

@Composable
fun ChoicePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) Anqi.palette.signal else Color.Transparent,
        contentColor = if (selected) Anqi.palette.onSignal else Anqi.palette.ink,
        border = BorderStroke(0.8.dp, if (selected) Anqi.palette.signal else Anqi.palette.line)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun WeekdayChips(
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (1..7).forEach { day ->
                ChoicePill(
                    text = weekdayName(day).removePrefix("周"),
                    selected = day in selected,
                    onClick = { onToggle(day) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun NumberGrid(
    values: List<Int>,
    selected: Int?,
    columns: Int,
    modifier: Modifier = Modifier,
    suffix: String = "",
    onSelect: (Int) -> Unit
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        values.chunked(columns).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { value ->
                    val isSelected = value == selected
                    Surface(
                        onClick = { onSelect(value) },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) Anqi.palette.signal else Color.Transparent,
                        contentColor = if (isSelected) Anqi.palette.onSignal else Anqi.palette.ink,
                        border = BorderStroke(0.8.dp, if (isSelected) Anqi.palette.signal else Anqi.palette.line)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$value$suffix",
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
