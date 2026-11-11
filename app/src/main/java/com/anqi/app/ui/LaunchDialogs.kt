package com.anqi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.anqi.app.alarm.LaunchCheck
import com.anqi.app.ui.theme.Anqi

/**
 * The one-line home-screen strip that summarises the startup check.
 *
 * Deliberately passive: it stays after the dialog is dismissed, because a
 * dismissed dialog is not a fixed phone. It never shouts, and it never claims a
 * switch is on.
 */
data class SetupHint(
    val title: String,
    val detail: String,
    /** What the strip's button says; decided where the sentence is written. */
    val action: String
)

/**
 * Turns a [LaunchCheck.Snapshot] into the strip's copy, or null when there is
 * nothing worth saying.
 *
 * Ordered by what the user can actually do right now, so the strip never asks
 * for a fix that is already done: a failed measurement first (something is
 * genuinely broken), then missing system permissions (one tap each), then the
 * three switches, and only then the two waiting states. The waiting states get
 * their own sentences rather than falling through to a count — "还差 0 项" is
 * the kind of wording that makes an app look broken when it is merely patient.
 *
 * Pure, so the wording is unit tested alongside the decision itself.
 */
fun setupHint(snapshot: LaunchCheck.Snapshot, oemLabel: String?): SetupHint? {
    val reasons = LaunchCheck.reasons(snapshot)
    if (reasons.isEmpty()) return null

    val missing = LaunchCheck.systemItems(snapshot).count { it.state != LaunchCheck.State.CONFIRMED }
    val manual = LaunchCheck.manualState(snapshot)
    val leftSec = if (snapshot.pendingAt > snapshot.now) {
        ((snapshot.pendingAt - snapshot.now) / 1000L).toInt() + 1
    } else {
        -1
    }

    return when {
        LaunchCheck.Reason.MEASUREMENT_FAILED in reasons -> SetupHint(
            title = "这三个开关没有生效",
            detail = "上次实测，应用被划掉之后没有响。多半是自启动或锁屏显示被系统关着。",
            action = "看看"
        )

        missing > 0 -> SetupHint(
            title = "还差 $missing 项系统权限",
            detail = if (manual == LaunchCheck.State.NEEDS_ACTION) {
                // No spaces around the brand: Chinese sets the name flush
                // against its particle, and spaces there read like a typo.
                "另外，${oemLabel ?: "这个系统"}的三个开关也需要手动打开。"
            } else {
                "通知、精确闹钟、全屏提醒任缺一项，提醒就可能不出现。"
            },
            action = "去打开"
        )

        manual == LaunchCheck.State.NEEDS_ACTION -> SetupHint(
            title = "还有三项开关没打开",
            detail = "${oemLabel ?: "这个系统"}要手动允许三项，否则清掉后台之后闹钟不响。",
            action = "去打开"
        )

        leftSec > 0 -> SetupHint(
            title = "约 $leftSec 秒后实测一次",
            detail = "响铃之前可以把应用划掉，这样测的才是真实情况。",
            action = "看看"
        )

        else -> SetupHint(
            title = "就看这一声响不响",
            detail = if (snapshot.pendingAt > 0) {
                "响过之后，这三项才算确认生效。"
            } else {
                "三项已经记录，等下一次实测通过就自动确认。"
            },
            action = "看看"
        )
    }
}

@Composable
fun SetupBanner(
    hint: SetupHint,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Anqi.palette.signalSoft,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    hint.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Anqi.palette.ink
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    hint.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Anqi.palette.muted
                )
            }
            AnqiTextButton(hint.action, onClick = onOpen, color = Anqi.palette.signal)
        }
    }
}

@Composable
private fun StateMark(state: LaunchCheck.State) {
    val (glyph, color) = when (state) {
        LaunchCheck.State.CONFIRMED -> "✓" to Anqi.palette.signal
        LaunchCheck.State.PENDING -> "·" to Anqi.palette.muted
        LaunchCheck.State.NEEDS_ACTION -> "!" to Anqi.palette.warning
    }
    Box(
        Modifier
            .size(20.dp)
            .background(color.copy(alpha = 0.16f), CircleShape)
            // The glyph carries the whole state, so it needs words for anyone
            // using a screen reader — and for the tests, which have no eyes.
            .semantics { contentDescription = LaunchCheck.stateText(state) },
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun CheckRow(view: LaunchCheck.ItemView, onFix: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StateMark(view.state)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                view.label,
                style = MaterialTheme.typography.bodyLarge,
                color = Anqi.palette.ink
            )
            Text(
                view.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = Anqi.palette.muted
            )
        }
        if (view.state == LaunchCheck.State.NEEDS_ACTION && onFix != null) {
            AnqiTextButton("去开", onClick = onFix, color = Anqi.palette.signal)
        }
    }
}

/**
 * The startup dialog.
 *
 * Wording care, since this is the screen that decides whether a user trusts the
 * app's own alarms: it never says a switch is on, it says who knows.
 * "已确认" means a real ring proved it; "待确认" means we are still waiting for
 * one. The system rows are the only ones stated as fact, because those are the
 * only ones the platform answers honestly.
 */
@Composable
fun OemCheckDialog(
    snapshot: LaunchCheck.Snapshot,
    oemLabel: String?,
    onFixSystem: (LaunchCheck.Item) -> Unit,
    onOpenSwitches: () -> Unit,
    onOpenDetails: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    val system = LaunchCheck.systemItems(snapshot)
    val missingSystem = system.count { it.state != LaunchCheck.State.CONFIRMED }
    val manual = LaunchCheck.manualState(snapshot)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Anqi.palette.surface,
        title = {
            Text(
                // Broken by hand: at headline size this sentence wraps to a
                // single orphaned character on a 360dp phone.
                text = "先确认这几项\n闹钟才靠得住",
                style = MaterialTheme.typography.headlineMedium,
                color = Anqi.palette.ink
            )
        },
        text = {
            Column {
                Text(
                    text = if (missingSystem > 0) {
                        "有 $missingSystem 项系统权限没开，提醒到点时可能什么都不出现。"
                    } else {
                        "系统权限都开了。剩下的是${oemLabel ?: "这个系统"}自己的开关——" +
                            "一旦把应用从后台清掉，它们决定闹钟还响不响。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Anqi.palette.muted
                )

                Spacer(Modifier.height(14.dp))
                SectionLabel("系统能自己确认")
                Spacer(Modifier.height(4.dp))
                system.forEach { view ->
                    CheckRow(
                        view = view,
                        onFix = if (view.state == LaunchCheck.State.NEEDS_ACTION) {
                            { onFixSystem(view.item) }
                        } else {
                            null
                        }
                    )
                }

                Spacer(Modifier.height(14.dp))
                SectionLabel("需要你手动打开的")
                Spacer(Modifier.height(4.dp))
                LaunchCheck.manualItems(snapshot).forEach { CheckRow(it) }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when (manual) {
                        LaunchCheck.State.CONFIRMED ->
                            "这三项上次已经实测确认过，可以不管。"

                        LaunchCheck.State.PENDING ->
                            "已经记录过了，就等一次实测来确认。"

                        LaunchCheck.State.NEEDS_ACTION ->
                            if (snapshot.confirmedAt > 0L) {
                                "你说开着，但那次实测没响。可能是其中一项没真正生效，再看一眼。"
                            } else {
                                "这三项不打开，应用被清掉之后闹钟就不会响。这是手机系统设的，不是应用能自己解决的。"
                            }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Anqi.palette.muted
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenSwitches) {
                Text("一键打开", color = Anqi.palette.signal)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onOpenDetails) {
                    Text("详细步骤", color = Anqi.palette.muted)
                }
                TextButton(onClick = onSnooze) {
                    Text("以后再说", color = Anqi.palette.muted)
                }
            }
        }
    )
}

/**
 * Asked when the user comes back from the switches screen.
 *
 * The point of this dialog is the sentence underneath the title: saying "都开了"
 * does not end the check, it starts a 60-second one. Being explicit about that
 * up front is what stops the "都开了" answer from feeling like a formality.
 */
@Composable
fun OemConfirmDialog(
    onConfirmed: () -> Unit,
    onLookAgain: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Anqi.palette.surface,
        title = {
            Text(
                text = "三项都打开了吗？",
                style = MaterialTheme.typography.headlineMedium,
                color = Anqi.palette.ink
            )
        },
        text = {
            Text(
                text = "选中「都开了」之后，我会在一分钟后响一次，用来确认真的生效——" +
                    "这一分钟里你可以放心把应用划掉。响了，这三项就算确认；没响，我会再提醒你。",
                style = MaterialTheme.typography.bodyMedium,
                color = Anqi.palette.muted
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirmed) {
                Text("都开了", color = Anqi.palette.signal)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                TextButton(onClick = onLookAgain) {
                    Text("再去看看", color = Anqi.palette.muted)
                }
                TextButton(onClick = onDismiss) {
                    Text("稍后", color = Anqi.palette.muted)
                }
            }
        }
    )
}
