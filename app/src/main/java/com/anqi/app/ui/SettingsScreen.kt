package com.anqi.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anqi.app.BuildConfig
import com.anqi.app.ui.theme.Anqi
import com.anqi.app.viewmodel.AlarmViewModel

@Composable
fun SettingsScreen(
    viewModel: AlarmViewModel,
    onBack: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    val readiness = viewModel.readiness
    var showAbout by remember { mutableStateOf(false) }
    var testHint by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = showAbout) { showAbout = false }
    if (showAbout) {
        AboutScreen(onBack = { showAbout = false })
        return
    }
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
                "设置",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = Anqi.palette.ink,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.width(52.dp))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
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
                SectionLabel("系统权限")
                Spacer(Modifier.height(10.dp))
                Text(
                    "精确闹钟需要这些权限才能准时响铃。国产系统可能还需要在系统设置里允许自启动和后台运行。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Anqi.palette.muted
                )
                Spacer(Modifier.height(14.dp))

                Surface(
                    color = Anqi.palette.surface,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                        PermissionRow(
                            title = "通知",
                            description = "显示提醒通知与全屏响铃",
                            granted = readiness.notifications,
                            onOpen = onRequestNotifications
                        )
                        Hairline(Modifier.padding(start = 21.dp))
                        PermissionRow(
                            title = "精确闹钟",
                            description = "在设定时间准时触发，而不是大约时间",
                            granted = readiness.exactAlarms,
                            onOpen = onOpenExactAlarmSettings
                        )
                        Hairline(Modifier.padding(start = 21.dp))
                        PermissionRow(
                            title = "全屏提醒",
                            description = "锁屏时直接显示响铃页面",
                            granted = readiness.fullScreen,
                            onOpen = onOpenFullScreenSettings
                        )
                    }
                }

                if (!readiness.allGood) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "建议全部开启。缺少精确闹钟权限时，系统可能延迟提醒。",
                        style = MaterialTheme.typography.labelMedium,
                        color = Anqi.palette.signal
                    )
                }

                Spacer(Modifier.height(16.dp))
                Surface(
                    color = Anqi.palette.raised,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "锁屏时不弹出？",
                            style = MaterialTheme.typography.titleMedium,
                            color = Anqi.palette.ink
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "小米、红米等系统还需要在应用信息里允许「锁屏显示」和「后台弹出界面」，否则系统可能把响铃页拦在后台。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Anqi.palette.muted
                        )
                        Spacer(Modifier.height(8.dp))
                        AnqiTextButton(
                            "打开应用信息",
                            onClick = onOpenAppSettings,
                            color = Anqi.palette.signal
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
                SectionLabel("后台守护")
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = Anqi.palette.surface,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "让提醒一直活着",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Anqi.palette.ink
                                )
                                Text(
                                    if (viewModel.guardEnabled) "已开启 · 状态栏有常驻通知" else "已关闭",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (viewModel.guardEnabled) Anqi.palette.muted else Anqi.palette.signal
                                )
                            }
                            Switch(
                                checked = viewModel.guardEnabled,
                                onCheckedChange = viewModel::updateGuardEnabled,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Anqi.palette.onSignal,
                                    checkedTrackColor = Anqi.palette.signal,
                                    uncheckedThumbColor = Anqi.palette.muted,
                                    uncheckedTrackColor = Color.Transparent,
                                    uncheckedBorderColor = Anqi.palette.line
                                )
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "开启后应用会保留一个低优先级常驻通知。这是系统允许应用长期驻留的唯一方式——清后台之所以会连闹钟一起清掉，就是因为进程没了，闹钟登记也一起没了。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Anqi.palette.muted
                        )
                        if (!viewModel.guardEnabled) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "关闭后如果系统清理了后台，提醒可能不再响，直到你再次打开本应用。",
                                style = MaterialTheme.typography.labelMedium,
                                color = Anqi.palette.signal
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Surface(
                    color = Anqi.palette.raised,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "别让系统把它清掉",
                            style = MaterialTheme.typography.titleMedium,
                            color = Anqi.palette.ink
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "还要三件事配合：加省电白名单、允许自启动、在最近任务里长按本应用卡片锁定它。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Anqi.palette.muted
                        )
                        Spacer(Modifier.height(8.dp))
                        AnqiTextButton(
                            "打开省电设置",
                            onClick = onOpenBatterySettings,
                            color = Anqi.palette.signal
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
                SectionLabel("提醒")
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = Anqi.palette.surface,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SnoozeRow(
                        minutes = viewModel.snoozeMinutes,
                        onChange = viewModel::updateSnoozeMinutes
                    )
                }
                Text(
                    "响铃后可以贪睡。时间范围 1–120 分钟。",
                    modifier = Modifier.padding(vertical = 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = Anqi.palette.muted
                )

                Spacer(Modifier.height(12.dp))
                Surface(
                    color = Anqi.palette.raised,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "试一次",
                            style = MaterialTheme.typography.titleMedium,
                            color = Anqi.palette.ink
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "十秒后响一次，不会改动任何闹钟的时间。响铃后把应用从后台清掉，再看它是否还响——这比等下个月更省事。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Anqi.palette.muted
                        )
                        Spacer(Modifier.height(8.dp))
                        AnqiTextButton(
                            "10 秒后响铃",
                            onClick = {
                                val target = viewModel.scheduleTestRing()
                                testHint = if (target == null) {
                                    "还没有开启的提醒，先新建一个再测。"
                                } else {
                                    "「${target.label.ifBlank { "提醒" }}」将在 10 秒后响铃。"
                                }
                            },
                            color = Anqi.palette.signal
                        )
                        testHint?.let { hint ->
                            Text(
                                hint,
                                style = MaterialTheme.typography.labelMedium,
                                color = Anqi.palette.signal
                            )
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
                SectionLabel("运行记录")
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = Anqi.palette.surface,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            if (viewModel.missedCount == 0) {
                                "没有漏掉的提醒。"
                            } else {
                                "有 ${viewModel.missedCount} 次到点没有响。"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (viewModel.missedCount == 0) Anqi.palette.ink else Anqi.palette.signal
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "下面是最近发生的排期、触发与被拦截记录，只存在本机。",
                            style = MaterialTheme.typography.labelMedium,
                            color = Anqi.palette.muted
                        )
                        Spacer(Modifier.height(10.dp))
                        if (viewModel.diagLines.isEmpty()) {
                            Text(
                                "还没有记录。",
                                style = MaterialTheme.typography.labelMedium,
                                color = Anqi.palette.muted
                            )
                        } else {
                            viewModel.diagLines.take(14).forEach { line ->
                                Text(
                                    line,
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Anqi.palette.muted,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            AnqiTextButton(
                                "刷新",
                                onClick = viewModel::refreshDiag,
                                color = Anqi.palette.muted
                            )
                            AnqiTextButton(
                                "清空",
                                onClick = viewModel::clearDiag,
                                color = Anqi.palette.signal
                            )
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))
                SectionLabel("关于")
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = Anqi.palette.surface,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            "月度闹钟",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Anqi.palette.ink
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "开源免费，只做可靠的月度与周期提醒。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Anqi.palette.muted
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "版本 ${BuildConfig.VERSION_NAME} · 无广告、不收费",
                            style = MaterialTheme.typography.labelMedium,
                            color = Anqi.palette.muted
                        )
                        Spacer(Modifier.height(6.dp))
                        AnqiTextButton(
                            "关于与声明",
                            onClick = { showAbout = true },
                            color = Anqi.palette.signal
                        )
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    onOpen: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(9.dp)
                .background(
                    if (granted) Anqi.palette.muted.copy(alpha = 0.45f) else Anqi.palette.signal,
                    RoundedCornerShape(3.dp)
                )
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = Anqi.palette.ink
            )
            Text(
                description,
                style = MaterialTheme.typography.labelMedium,
                color = Anqi.palette.muted
            )
        }
        if (!granted) {
            AnqiTextButton("开启", onOpen, color = Anqi.palette.signal)
        } else {
            Text(
                "已开启",
                style = MaterialTheme.typography.labelMedium,
                color = Anqi.palette.muted
            )
        }
    }
}

@Composable
private fun SnoozeRow(minutes: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "贪睡时长",
                style = MaterialTheme.typography.bodyLarge,
                color = Anqi.palette.ink
            )
            Text(
                "$minutes 分钟",
                style = MaterialTheme.typography.labelMedium,
                color = Anqi.palette.muted
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            AnqiTextButton("−", { onChange((minutes - 1).coerceAtLeast(1)) }, color = Anqi.palette.muted)
            AnqiTextButton("+", { onChange((minutes + 1).coerceAtMost(120)) }, color = Anqi.palette.signal)
        }
    }
}
