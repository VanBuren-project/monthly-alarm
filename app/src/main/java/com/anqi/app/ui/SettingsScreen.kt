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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.anqi.app.alarm.LaunchCheck
import com.anqi.app.ui.theme.Anqi
import com.anqi.app.viewmodel.AlarmViewModel
import kotlinx.coroutines.delay

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

                Spacer(Modifier.height(14.dp))
                LaunchCheckCard(viewModel)

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
                SectionLabel("清后台还响不响")
                Spacer(Modifier.height(10.dp))
                SwipeTestCard(viewModel)

                Spacer(Modifier.height(32.dp))
                SectionLabel("别让系统清掉它")
                Spacer(Modifier.height(10.dp))
                OemSetupCard(viewModel)

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
                                    "多一层保险",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Anqi.palette.ink
                                )
                                Text(
                                    if (viewModel.guardEnabled) "已开启 · 状态栏有一个常驻通知" else "已关闭",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Anqi.palette.muted
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
                            "提醒本身已经不依赖应用留在后台了：到点那一刻由系统直接把响铃服务拉起来，跟手机自带闹钟一样。这一个开关只是再叠一层——让进程尽量别被杀，省掉每次重建的开销。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Anqi.palette.muted
                        )
                        if (!viewModel.guardEnabled) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "关掉也不影响到点响铃，只是应用被清理后要重新启动一次才恢复。",
                                style = MaterialTheme.typography.labelMedium,
                                color = Anqi.palette.muted
                            )
                        }
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
private fun SwipeTestCard(viewModel: AlarmViewModel) {
    val verdict = viewModel.swipeVerdict
    val pending = viewModel.swipePending
    var hint by remember { mutableStateOf<String?>(null) }

    // The countdown is the only thing on this screen that moves, so it drives its
    // own tick: without it the seconds would sit frozen until the next 30-second
    // recomposition and the user would think the test had stalled.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(pending) {
        while (pending) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    Surface(
        color = Anqi.palette.surface,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                when {
                    pending -> "正在实测"
                    verdict == null -> "先测一次，别等下个月"
                    verdict.late -> "上次实测：响了，但迟了很久"
                    verdict.rang && verdict.swiped -> "上次实测：通过"
                    verdict.rang -> "上次实测：响了，但没测到清后台"
                    else -> "上次实测：没响"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (verdict != null && !verdict.rang) Anqi.palette.signal else Anqi.palette.ink
            )
            Spacer(Modifier.height(5.dp))
            Text(
                when {
                    pending -> {
                        val seconds = ((viewModel.swipePendingAt - now) / 1000).coerceAtLeast(0)
                        "$seconds 秒后响铃。现在就把应用从最近任务里划掉，然后等它响——" +
                            "别打开应用，打开了就不算数了。"
                    }

                    verdict == null ->
                        "一分钟后就响一次。响之前把应用从最近任务划掉，看它还响不响。" +
                            "这是唯一能确定「清后台之后还能不能提醒」的办法，不用等下个月。"

                    verdict.late ->
                        "提醒最终还是响了，但没有按时响——系统把它压了很久。这意味着夜里它也可能迟到，" +
                            "请把下面的省电策略改成「无限制」，然后再测一次。"

                    verdict.rang && verdict.swiped ->
                        "应用被划掉、进程也没了，提醒还是响了（" +
                            (if (verdict.path == "service") "由系统直接唤起响铃" else "由广播送达") +
                            "）。这台手机现在是可靠的。"

                    verdict.rang ->
                        "提醒响了，说明响铃本身没问题；但进程中途没有被杀掉，" +
                            "所以这次没测到「清后台」。想测它，请在响铃之前就划掉应用，然后先别打开它。"

                    else ->
                        "排期的那次提醒没有响。说明这台手机把应用划掉之后就冻住了，" +
                            "系统不再给它任何触发。请先完成下面的三步设置，再测一次。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Anqi.palette.muted
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (pending) {
                    AnqiTextButton(
                        "取消实测",
                        onClick = { viewModel.cancelSwipeTest(); hint = null },
                        color = Anqi.palette.muted
                    )
                } else {
                    AnqiTextButton(
                        "开始实测",
                        onClick = {
                            val target = viewModel.startSwipeTest()
                            hint = if (target == null) {
                                "还没有启用的提醒，先新建一个再测。"
                            } else {
                                "已排期，「${target.label.ifBlank { "提醒" }}」将在一分钟后响。现在划掉应用。"
                            }
                        },
                        color = Anqi.palette.signal
                    )
                }
            }
            hint?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = Anqi.palette.signal
                )
            }
        }
    }
}

@Composable
private fun OemSetupCard(viewModel: AlarmViewModel) {
    Surface(
        color = Anqi.palette.raised,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "三步就好",
                style = MaterialTheme.typography.titleMedium,
                color = Anqi.palette.ink
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "国产系统把应用从最近任务划掉之后会直接冻住它，不再送闹钟。" +
                    "这是系统的策略，代码绕不过去；把这三件打开就解除了。",
                style = MaterialTheme.typography.bodyMedium,
                color = Anqi.palette.muted
            )
            Spacer(Modifier.height(12.dp))
            SetupStep(
                index = "1",
                title = "允许自启动",
                detail = "不然划掉之后系统不允许它自己醒过来。",
                action = "打开自启动设置",
                onOpen = { viewModel.openAutostart() }
            )
            SetupStep(
                index = "2",
                title = "省电策略改为「无限制」",
                detail = "省电模式会在夜里把后台整个冻住。",
                action = "打开省电设置",
                onOpen = { viewModel.openBatterySettings() },
                // The only one of the three the app can actually check, so it is
                // the only one that gets a checkmark instead of a request.
                verified = viewModel.batteryUnrestricted
            )
            SetupStep(
                index = "3",
                title = "在最近任务里锁定本应用",
                detail = "长按本应用的卡片，选锁形图标；锁定的应用不会被一键清理掉。",
                action = "打开应用信息",
                onOpen = { viewModel.openAppInfo() }
            )
            Spacer(Modifier.height(6.dp))
            if (viewModel.setupDone) {
                Text(
                    "已记录。之后每次实测都会验证这三步是不是真的生效了。",
                    style = MaterialTheme.typography.labelMedium,
                    color = Anqi.palette.muted
                )
            } else {
                AnqiTextButton(
                    "这三步我已经设好了",
                    onClick = viewModel::markSetupDone,
                    color = Anqi.palette.signal
                )
            }
        }
    }
}

@Composable
private fun LaunchCheckCard(viewModel: AlarmViewModel) {
    val snapshot = viewModel.launchState
    val confirmedAt = viewModel.setupConfirmedAt
    val manual = if (snapshot == null) null else LaunchCheck.manualState(snapshot)
    val missing = if (snapshot == null) 0 else {
        LaunchCheck.systemItems(snapshot).count { it.state != LaunchCheck.State.CONFIRMED }
    }

    // An unread snapshot only happens before the first foreground; saying
    // nothing is better than saying "0 项" as if it had been measured.
    if (snapshot == null) return

    val headline = LaunchCheck.summary(snapshot)
    val tone = when {
        missing > 0 || manual == LaunchCheck.State.NEEDS_ACTION -> Anqi.palette.signal
        else -> Anqi.palette.muted
    }
    val whenText = if (confirmedAt > 0L) {
        "上次确认：${formatStamp(confirmedAt)}"
    } else {
        "还没有确认过这三项"
    }

    Surface(
        color = Anqi.palette.surface,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "上次检查",
                style = MaterialTheme.typography.titleMedium,
                color = Anqi.palette.ink
            )
            Spacer(Modifier.height(5.dp))
            Text(
                headline,
                style = MaterialTheme.typography.bodyMedium,
                color = tone
            )
            Spacer(Modifier.height(3.dp))
            Text(
                whenText,
                style = MaterialTheme.typography.labelMedium,
                color = Anqi.palette.muted
            )
            Spacer(Modifier.height(8.dp))
            AnqiTextButton(
                "重新检查",
                onClick = { viewModel.showLaunchCheck() },
                color = Anqi.palette.signal
            )
        }
    }
}

@Composable
private fun SetupStep(
    index: String,
    title: String,
    detail: String,
    action: String,
    onOpen: () -> Unit,
    verified: Boolean? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .size(20.dp)
                .background(
                    if (verified == true) Anqi.palette.signal else Anqi.palette.signalSoft,
                    RoundedCornerShape(7.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (verified == true) "✓" else index,
                style = MaterialTheme.typography.labelMedium,
                color = if (verified == true) Anqi.palette.onSignal else Anqi.palette.signal
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = Anqi.palette.ink
            )
            Text(
                detail,
                style = MaterialTheme.typography.labelMedium,
                color = Anqi.palette.muted
            )
            if (verified == true) {
                Text(
                    "已经是无限制了",
                    style = MaterialTheme.typography.labelMedium,
                    color = Anqi.palette.signal
                )
            } else {
                AnqiTextButton(action, onClick = onOpen, color = Anqi.palette.signal)
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
