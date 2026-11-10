package com.anqi.app

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                alarmState?.let(::dismiss)
            }
        })
        loadAlarm(intent)

        setContent {
            val alarm = alarmState
            AnqiTheme(darkTheme = true) {
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
