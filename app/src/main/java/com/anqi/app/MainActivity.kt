package com.anqi.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import com.anqi.app.alarm.AlarmScheduler
import com.anqi.app.alarm.RingtoneService
import com.anqi.app.alarm.SelfHeal
import com.anqi.app.ui.AnqiRoot
import com.anqi.app.ui.theme.AnqiTheme
import com.anqi.app.viewmodel.AlarmViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: AlarmViewModel by lazy {
        ViewModelProvider(this)[AlarmViewModel::class.java]
    }

    /**
     * The ring we already pushed [AlarmActivity] for. Keyed by the ring token,
     * not the alarm id, so a snoozed alarm that rings again still takes over
     * the screen. Without it the activity would bounce the user right back
     * into the ring screen after every dismissal, because the service may not
     * have torn down yet.
     */
    private var routedRingToken = 0L

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.refreshReadiness()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnqiTheme {
                AnqiRoot(
                    viewModel = viewModel,
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onOpenExactAlarmSettings = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData("package:$packageName".toUri())
                            runCatching { startActivity(intent) }
                                .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                        } else {
                            startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    },
                    onOpenFullScreenSettings = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                                .setData("package:$packageName".toUri())
                            runCatching { startActivity(intent) }
                                .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                        } else {
                            openAppSettings()
                        }
                    },
                    onOpenAppSettings = ::openAppSettings,
                    onOpenBatterySettings = ::openBatterySettings
                )
            }
        }
        openRingingAlarmIfAny()
    }

    /**
     * If an alarm is ringing right now, the list is the wrong screen to land on.
     * Opening the app (from the launcher, a notification, anywhere) should lead
     * straight to the dismiss screen instead of making the user hunt for it.
     */
    private fun openRingingAlarmIfAny() {
        val id = RingtoneService.activeAlarmId
        val token = RingtoneService.activeRingToken
        if (id <= 0L) {
            // Nothing ringing any more: forget the routing so the next ring of
            // the same alarm (a snooze, say) still takes over the screen.
            routedRingToken = 0L
            return
        }
        if (token == routedRingToken) return
        routedRingToken = token
        startActivity(
            Intent(this, AlarmActivity::class.java)
                .putExtra(AlarmScheduler.EXTRA_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData("package:$packageName".toUri())
        runCatching { startActivity(intent) }
            .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    /**
     * Deliberately opens the system battery-optimisation list instead of asking
     * for `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: the direct whitelist dialog is
     * a Play policy violation, while pointing at the list is both allowed and
     * what actually helps on ROMs that kill background apps.
     */
    private fun openBatterySettings() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        runCatching { startActivity(intent) }
            .onFailure { openAppSettings() }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshReadiness()
        // Also check here, not only in onCreate: the app may already be alive
        // (and simply backgrounded) when the full-screen intent gets blocked,
        // in which case onCreate never runs again.
        openRingingAlarmIfAny()
        // Opening the app is the moment the user is looking for a missing
        // reminder, so it is also the right moment to repair the schedule and to
        // find out whether anything was silently skipped.
        val late = SelfHeal.heal(this, ensureGuard = true)
        if (late > 0) viewModel.refreshDiag()
        // Last, so the startup check reads the state SelfHeal just repaired and a
        // freshly settled swipe test is already visible to it.
        viewModel.onForeground()
    }
}
