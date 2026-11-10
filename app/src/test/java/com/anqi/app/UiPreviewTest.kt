package com.anqi.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anqi.app.data.AlarmStore
import com.anqi.app.model.Alarm
import com.anqi.app.model.RepeatType
import com.anqi.app.ui.AnqiRoot
import com.anqi.app.ui.RingingScreen
import com.anqi.app.ui.theme.AnqiTheme
import com.anqi.app.viewmodel.AlarmViewModel
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
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

    private fun capture(name: String) {
        val dir = File("build/screens").apply { mkdirs() }
        val view = compose.activity.window.decorView
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
        assertTrue(
            compose.onAllNodesWithText("别让系统把它清掉").fetchSemanticsNodes().isNotEmpty()
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

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun phoneSettingsDiagnostics() {
        showHome()
        compose.onNodeWithText("设置").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("没有漏掉的提醒。").performScrollTo()
        compose.waitForIdle()
        assertTrue(
            compose.onAllNodesWithText("没有漏掉的提醒。").fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            compose.onAllNodesWithText("刷新").fetchSemanticsNodes().isNotEmpty()
        )
        capture("settings-diag-360")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun phoneSettingsTestRing() {
        showHome()
        compose.onNodeWithText("设置").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("10 秒后响铃").performScrollTo().performClick()
        compose.waitForIdle()
        // The confirmation names the reminder that will ring, so the user can
        // tell the test apart from a real reminder when it fires.
        val hint = "「交电费」将在 10 秒后响铃。"
        assertTrue(compose.onAllNodesWithText(hint).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText(hint).performScrollTo()
        compose.waitForIdle()
        capture("settings-test-360")
    }

    private fun alarmForRinging() = Alarm(
        id = 7L,
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
