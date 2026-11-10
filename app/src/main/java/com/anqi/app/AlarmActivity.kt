package com.anqi.app

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.anqi.app.alarm.AlarmReceiver
import com.anqi.app.alarm.AlarmScheduler
import com.anqi.app.data.AlarmStore
import com.anqi.app.model.Alarm
import com.anqi.app.ui.RingingScreen
import com.anqi.app.ui.theme.AnqiTheme

class AlarmActivity : ComponentActivity() {
    private var alarmState by mutableStateOf<Alarm?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showOverLockScreen()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                alarmState?.let(::dismiss)
            }
        })
        loadAlarm(intent)

        setContent {
            val alarm = alarmState
            // Follow the system theme, exactly like the main app: forcing a dark
            // canvas here is what made the ringing page look like a foreign,
            // all-black screen next to the app's warm light theme.
            AnqiTheme {
                if (alarm != null) {
                    RingingScreen(
                        alarm = alarm,
                        snoozeMinutes = AlarmStore(this).snoozeMinutes,
                        onSnooze = { snooze(alarm) },
                        onDismiss = { dismiss(alarm) }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadAlarm(intent)
    }

    /**
     * Two independent things have to happen for an alarm to be usable from a
     * sleeping phone: the display must turn on, and the activity must be allowed
     * to draw above the keyguard. `setTurnScreenOn` covers the first on API 27+;
     * the window flags are the pre-27 path and also cover ROMs that ignore the
     * newer API.
     */
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        // Dismissing is best-effort: on a secured lock screen the request is
        // simply ignored, and the activity still shows above the keyguard.
        val keyguard = getSystemService(KeyguardManager::class.java)
        runCatching { keyguard?.requestDismissKeyguard(this, null) }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event?.repeatCount == 0) alarmState?.let(::snooze)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun loadAlarm(intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1L)
        alarmState = AlarmStore(this).find(id)
        if (alarmState == null) finish()
    }

    private fun snooze(alarm: Alarm) {
        sendBroadcast(
            Intent(this, AlarmReceiver::class.java)
                .setAction(AlarmScheduler.ACTION_SNOOZE)
                .putExtra(AlarmScheduler.EXTRA_ID, alarm.id)
        )
        finish()
    }

    private fun dismiss(alarm: Alarm) {
        sendBroadcast(
            Intent(this, AlarmReceiver::class.java)
                .setAction(AlarmScheduler.ACTION_DISMISS)
                .putExtra(AlarmScheduler.EXTRA_ID, alarm.id)
        )
        finish()
    }
}
