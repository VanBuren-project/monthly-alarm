package com.anqi.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anqi.app.alarm.LaunchCheck
import com.anqi.app.data.AlarmStore
import com.anqi.app.model.Alarm
import com.anqi.app.model.RepeatType
import com.anqi.app.ui.AnqiRoot
import com.anqi.app.ui.OemCheckDialog
import com.anqi.app.ui.OemConfirmDialog
import com.anqi.app.ui.RingingScreen
import com.anqi.app.ui.SetupBanner
import com.anqi.app.ui.setupHint
import com.anqi.app.ui.theme.AnqiTheme
import com.anqi.app.viewmodel.AlarmViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Desktop-side Compose smoke test. Runs on the JVM through Robolectric, so it
 * exercises the real Compose tree without an emulator or hardware virtualisation.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiPreviewTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: Application

    @Before
    fun seed() {
        app = ApplicationProvider.getApplicationContext()
        AlarmStore(app).apply {
            saveAll(emptyList())
            upsert(
                Alarm(
                    label = "交电费",
                    repeatType = RepeatType.MONTHLY_DAY,
                    monthlyDay = 8,
                    hour = 8,
                    minute = 0,
                    date = LocalDate.now().plusDays(1)
                )
            )
        }
    }

    private fun showHome() {
        val vm = AlarmViewModel(app)
        compose.setContent {
            AnqiTheme(darkTheme = true) {
                AnqiRoot(
                    viewModel = vm,
                    onRequestNotifications = {},
                    onOpenExactAlarmSettings = {},
                    onOpenFullScreenSettings = {},
                    onOpenAppSettings = {},
                    onOpenBatterySettings = {}
                )
            }
        }
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText("月度闹钟").fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("下一次").fetchSemanticsNodes().isNotEmpty())
        assertTrue(
            compose.onAllNodesWithText("交电费", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        )
    }

    /**
     * A Compose `Dialog` draws into its own window, so the activity's decor view
     * is blank behind it. Capture whichever window actually holds the pixels.
     */
    private fun capture(name: String) {
        val dir = File("build/screens").apply { mkdirs() }
        val dialog = ShadowDialog.getLatestDialog()
        val dialogView = dialog?.takeIf { it.isShowing }?.window?.decorView
        val view = dialogView?.takeIf { it.width > 0 && it.height > 0 }
            ?: compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(dir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun phoneHome() {
        showHome()
        capture("home-360")
    }

    @Test
    @Config(qualifiers = "w840dp-h1000dp-xhdpi")
    fun tabletHome() {
        showHome()
        capture("home-840")
    }

    @Test
    @Config(qualifiers = "w840dp-h1000dp-xhdpi")
    fun tabletYear() {
        showHome()
        compose.onNodeWithTag("tab-year").performClick()
        compose.waitForIdle()
        assertTrue(
            compose.onAllNodesWithTag("pane-title").fetchSemanticsNodes().isNotEmpty()
        )
        capture("year-840")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun editorScreen() {
        showHome()
        compose.onNodeWithText("新建提醒").performClick()
        compose.waitForIdle()
        assertTrue(
            compose.onAllNodesWithText("新建提醒").fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            compose.onAllNodesWithText("保存").fetchSemanticsNodes().isNotEmpty()
        )
        capture("editor-411")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun phoneYear() {
        showHome()
        compose.onNodeWithTag("tab-year").performClick()
        compose.waitForIdle()
        assertTrue(
            compose.onAllNodesWithTag("pane-title").fetchSemanticsNodes().isNotEmpty()
        )
        capture("year-360")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun phoneMonth() {
        showHome()
        compose.onNodeWithTag("tab-month").performClick()
        compose.waitForIdle()
        assertTrue(
            compose.onAllNodesWithTag("pane-title").fetchSemanticsNodes().isNotEmpty()
        )
        capture("month-360")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun phoneWeek() {
        showHome()
        compose.onNodeWithTag("tab-week").performClick()
        compose.waitForIdle()
        assertTrue(
            compose.onAllNodesWithTag("pane-title").fetchSemanticsNodes().isNotEmpty()
        )
        capture("week-360")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun phoneWeekWithEvent() {
        val next = ZonedDateTime.now().plusHours(1)
        AlarmStore(app).upsert(
            Alarm(
                label = "取快递",
                repeatType = RepeatType.ONCE,
                date = next.toLocalDate(),
                hour = next.hour,
                minute = 0
            )
        )
        showHome()
        compose.onNodeWithTag("tab-week").performClick()
        compose.waitForIdle()
        assertTrue(
            compose.onAllNodesWithTag("pane-title").fetchSemanticsNodes().isNotEmpty()
        )
        capture("week-event-360")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun phoneAbout() {
        showHome()
        compose.onNodeWithText("设置").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("关于与声明").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(
            compose.onAllNodesWithText("范布伦计划").fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            compose.onAllNodesWithText("开源免费", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        )
        capture("about-360")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun phoneSettings() {
        showHome()
        compose.onNodeWithText("设置").performClick()
        compose.waitForIdle()
        assertTrue(
            compose.onAllNodesWithText("系统权限").fetchSemanticsNodes().isNotEmpty()
        )
        // 1.3.8 removes the measured-test and diagnostic surfaces from the app:
        // they were field instruments for the "清后台之后还响不响" investigation,
        // not something a user should be shown.
        assertTrue(
            compose.onAllNodesWithText("别让系统清掉它").fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            compose.onAllNodesWithText("三步就好").fetchSemanticsNodes().isNotEmpty()
        )
        // 1.3.5 made delivery independent of a resident process (the exact alarm
        // starts the ringing service itself, like the stock clock), so the guard
        // is now presented as an optional extra layer rather than a requirement.
        assertTrue(
            compose.onAllNodesWithText("后台守护").fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            compose.onAllNodesWithText("多一层保险").fetchSemanticsNodes().isNotEmpty()
        )
        capture("settings-360")
    }

    /**
     * The three OEM steps are the actual answer to "清后台之后不响", so they get
     * a shot of their own — a deliverable image that cuts the fix off is worse
     * than none.
     */
    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun phoneSettingsOemSteps() {
        showHome()
        compose.onNodeWithText("设置").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("这三步我已经设好了").performScrollTo()
        compose.waitForIdle()
        assertTrue(
            compose.onAllNodesWithText("三步就好").fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            compose.onAllNodesWithText("允许自启动").fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            compose.onAllNodesWithText("省电策略改为「无限制」").fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            compose.onAllNodesWithText("在最近任务里锁定本应用").fetchSemanticsNodes().isNotEmpty()
        )
        capture("settings-oem-360")
    }

    /**
     * The startup check is the screen that decides whether the user trusts this
     * app's own alarms, so its wording is the feature: it must never print a
     * switch as "已开启", because nothing on the platform will tell us that.
     */
    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun launchCheckDialog() {
        val snapshot = LaunchCheck.Snapshot(
            notifications = true,
            exactAlarms = true,
            fullScreen = true,
            batteryUnrestricted = false,
            confirmedAt = 0L,
            now = System.currentTimeMillis()
        )
        compose.setContent {
            AnqiTheme(darkTheme = true) {
                OemCheckDialog(
                    snapshot = snapshot,
                    oemLabel = "小米 / 红米",
                    onFixSystem = {},
                    onOpenSwitches = {},
                    onOpenDetails = {},
                    onSnooze = {},
                    onDismiss = {}
                )
            }
        }
        compose.waitForIdle()

        assertTrue(
            compose.onAllNodesWithText("先确认这几项", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        )
        // The two groups are named by who knows the answer, not by severity.
        assertTrue(
            compose.onAllNodesWithText("系统能自己确认").fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            compose.onAllNodesWithText("需要你手动打开的").fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            compose.onAllNodesWithText("允许自启动").fetchSemanticsNodes().isNotEmpty()
        )
        // The state lives in the row's mark, which is a glyph plus a content
        // description — and it must be the honest one, never "已开启". This
        // snapshot leaves battery unrestricted = false, so it is one system row
        // plus the three manual ones.
        assertEquals(
            4,
            compose.onAllNodesWithContentDescription("需要你处理").fetchSemanticsNodes().size
        )
        assertTrue(
            compose.onAllNodesWithContentDescription("已开启").fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            compose.onAllNodesWithText("已开启").fetchSemanticsNodes().isEmpty()
        )
        capture("launch-check-360")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun launchConfirmDialog() {
        compose.setContent {
            AnqiTheme(darkTheme = true) {
                OemConfirmDialog(onConfirmed = {}, onLookAgain = {}, onDismiss = {})
            }
        }
        compose.waitForIdle()

        assertTrue(
            compose.onAllNodesWithText("三项都打开了吗？").fetchSemanticsNodes().isNotEmpty()
        )
        // The dialog has to say that "都开了" starts a test, not ends the check.
        assertTrue(
            compose.onAllNodesWithText("一分钟后响一次", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(compose.onAllNodesWithText("都开了").fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("再去看看").fetchSemanticsNodes().isNotEmpty())
        capture("launch-confirm-360")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun setupBanner() {
        val hint = setupHint(
            LaunchCheck.Snapshot(
                notifications = true,
                exactAlarms = true,
                fullScreen = true,
                batteryUnrestricted = true,
                confirmedAt = 0L,
                now = System.currentTimeMillis()
            ),
            oemLabel = "小米 / 红米"
        )
        assertTrue("a phone that was never set up must get a strip", hint != null)
        compose.setContent {
            AnqiTheme(darkTheme = true) {
                Box(Modifier.padding(20.dp)) {
                    SetupBanner(hint = hint!!, onOpen = {})
                }
            }
        }
        compose.waitForIdle()

        assertTrue(
            compose.onAllNodesWithText("还有三项开关没打开").fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(compose.onAllNodesWithText("去打开").fetchSemanticsNodes().isNotEmpty())
        capture("setup-banner-360")
    }

    private fun alarmForRinging() = Alarm(        id = 7L,
        label = "交电费",
        note = "这个月有 7 折优惠，记得先在 App 里领券再交。",
        repeatType = RepeatType.MONTHLY_DAY,
        monthlyDay = 8,
        hour = 8,
        minute = 0,
        date = LocalDate.now(),
        vibrate = true
    )

    /**
     * Guards the "black screen" regression: the ringing face must inherit the app
     * palette in both modes, so its canvas has to match the window background that
     * [com.anqi.app.AlarmActivity] uses for the very first frame.
     */
    private fun showRinging(dark: Boolean) {
        compose.setContent {
            AnqiTheme(darkTheme = dark) {
                RingingScreen(
                    alarm = alarmForRinging(),
                    snoozeMinutes = 5,
                    onSnooze = {},
                    onDismiss = {}
                )
            }
        }
        compose.waitForIdle()
        assertTrue(
            compose.onAllNodesWithTag("ringing-screen").fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            compose.onAllNodesWithText("正在响铃").fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            compose.onAllNodesWithText("停止后", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        )
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun ringingDark() {
        showRinging(dark = true)
        capture("ringing-dark-360")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun ringingLight() {
        showRinging(dark = false)
        capture("ringing-light-360")
    }
}
