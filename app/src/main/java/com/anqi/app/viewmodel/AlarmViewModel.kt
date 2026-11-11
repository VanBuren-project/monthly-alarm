package com.anqi.app.viewmodel

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.anqi.app.alarm.AlarmScheduler
import com.anqi.app.alarm.ArmLedger
import com.anqi.app.alarm.Diag
import com.anqi.app.alarm.GuardJob
import com.anqi.app.alarm.GuardService
import com.anqi.app.alarm.LaunchCheck
import com.anqi.app.alarm.OemAutostart
import com.anqi.app.alarm.SwipeTest
import com.anqi.app.data.AlarmStore
import com.anqi.app.data.NextOccurrence
import com.anqi.app.model.Alarm

data class SystemReadiness(
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val fullScreen: Boolean
) {
    val allGood: Boolean get() = notifications && exactAlarms && fullScreen
}

class AlarmViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AlarmStore(application)
    private val scheduler = AlarmScheduler(application)

    val alarms = mutableStateListOf<Alarm>()
    var readiness by mutableStateOf(checkReadiness())
        private set
    var snoozeMinutes by mutableIntStateOf(store.snoozeMinutes)
        private set

    /** Whether the foreground guard is switched on; toggled from settings. */
    var guardEnabled by mutableStateOf(GuardService.isWanted(application))
        private set

    /** How many reminders we armed and the system then silently dropped. */
    var missedCount by mutableIntStateOf(ArmLedger.missedCount(application))
        private set

    var diagLines by mutableStateOf(Diag.read(application).reversed())
        private set

    /** Whether the OEM autostart / recents-lock walkthrough has been seen. */
    var setupDone by mutableStateOf(SwipeTest.setupDone(application))
        private set

    /** When the user last said the three switches were on; 0 when never. */
    var setupConfirmedAt by mutableLongStateOf(SwipeTest.setupConfirmedAt(application))
        private set

    /** OEM name for the copy, or null when we have no specific screen for it. */
    val oemLabel: String? = OemAutostart.label(application)

    /** Whether the system already exempts us from battery optimisation. */
    var batteryUnrestricted by mutableStateOf(OemAutostart.isBatteryUnrestricted(application))
        private set

    /** True while a swipe test is armed and its slot has not been judged yet. */
    var swipePending by mutableStateOf(SwipeTest.isPending(application))
        private set

    /** When the armed swipe test is due, for the countdown in settings. */
    var swipePendingAt by mutableLongStateOf(SwipeTest.pendingAt(application))
        private set

    /** The last measured swipe test, or null when it was never run. */
    var swipeVerdict by mutableStateOf(SwipeTest.verdict(application))
        private set

    // ------------------------------------------------------------ start check

    /** The startup check's latest reading, or null before the first foreground. */
    var launchState by mutableStateOf<LaunchCheck.Snapshot?>(null)
        private set

    /** Whether the "还差几项" dialog should be on screen. */
    var launchDialog by mutableStateOf(false)
        private set

    /** Whether to ask "三项都打开了吗？" after the user came back from settings. */
    var launchReturnConfirm by mutableStateOf(false)
        private set

    private var sawForeground = false
    private var leftForSettings = false

    init {
        reload()
    }

    fun reload() {
        val loaded = store.all().sortedWith(
            compareByDescending<Alarm> { it.enabled }
                .thenBy { alarm ->
                    NextOccurrence.next(alarm)?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE
                }
        )
        alarms.clear()
        alarms.addAll(loaded)
        refreshReadiness()
    }

    fun refreshReadiness() {
        readiness = checkReadiness()
        missedCount = ArmLedger.missedCount(getApplication())
        swipePending = SwipeTest.isPending(getApplication())
        swipePendingAt = SwipeTest.pendingAt(getApplication())
        swipeVerdict = SwipeTest.verdict(getApplication())
        setupDone = SwipeTest.setupDone(getApplication())
        setupConfirmedAt = SwipeTest.setupConfirmedAt(getApplication())
        batteryUnrestricted = OemAutostart.isBatteryUnrestricted(getApplication())
        // Re-read here too, so the home banner and the settings summary follow
        // every action that changes the picture (start/cancel a test, confirm the
        // switches) without waiting for the next foreground.
        launchState = launchSnapshot()
    }

    fun save(alarm: Alarm): Alarm {
        val normalized = if (alarm.repeatType == com.anqi.app.model.RepeatType.ONCE && alarm.date == null) {
            alarm.copy(date = java.time.LocalDate.now().plusDays(1))
        } else {
            alarm
        }
        val saved = store.upsert(normalized)
        scheduler.schedule(saved)
        reload()
        return saved
    }

    fun delete(id: Long) {
        scheduler.cancel(id)
        ArmLedger.clearArmed(getApplication(), id)
        store.delete(id)
        reload()
    }

    fun setEnabled(alarm: Alarm, enabled: Boolean) {
        val updated = store.upsert(alarm.copy(enabled = enabled))
        if (enabled) scheduler.schedule(updated) else scheduler.cancel(updated.id)
        reload()
    }

    fun updateSnoozeMinutes(minutes: Int) {
        store.snoozeMinutes = minutes
        snoozeMinutes = store.snoozeMinutes
    }

    fun updateGuardEnabled(enabled: Boolean) {
        val context = getApplication<Application>()
        if (enabled) {
            GuardService.enable(context)
            GuardJob.schedule(context)
            scheduler.rescheduleAll(store.all())
            Diag.log(context, "GUARD 用户开启守护")
        } else {
            GuardService.shutdown(context)
            Diag.log(context, "GUARD 用户关闭守护")
        }
        guardEnabled = GuardService.isWanted(context)
    }

    /** Pulls the newest log lines after something happened that wrote to them. */
    fun refreshDiag() {
        diagLines = Diag.read(getApplication()).reversed()
        missedCount = ArmLedger.missedCount(getApplication())
    }

    fun clearDiag() {
        val context = getApplication<Application>()
        Diag.clear(context)
        ArmLedger.resetMissed(context)
        refreshDiag()
    }

    /**
     * Rings the first enabled reminder ten seconds from now without touching
     * its schedule. Returns the reminder used, or null when there is nothing to
     * test with — the settings screen turns that into a readable hint.
     */
    fun scheduleTestRing(): Alarm? {
        val target = alarms.firstOrNull { it.enabled } ?: return null
        scheduler.scheduleTestRing(target)
        Diag.log(getApplication(), "TEST 已排期 10 秒后", "#${target.id}")
        refreshDiag()
        return target
    }

    /**
     * Arms the swipe test against the first enabled reminder.
     *
     * @return the reminder used, or null when there is nothing to ring with
     *   (a test with no reminder has nothing to load when it fires).
     */
    fun startSwipeTest(): Alarm? {
        val target = alarms.firstOrNull { it.enabled } ?: return null
        if (!SwipeTest.start(getApplication(), target.id)) return null
        refreshReadiness()
        refreshDiag()
        return target
    }

    fun cancelSwipeTest() {
        SwipeTest.cancel(getApplication())
        refreshReadiness()
        refreshDiag()
    }

    fun markSetupDone() {
        SwipeTest.markSetupDone(getApplication())
        setupDone = true
    }

    // --------------------------------------------------------- startup prompt

    /** Everything [LaunchCheck] needs, read fresh from the system. */
    fun launchSnapshot(now: Long = System.currentTimeMillis()): LaunchCheck.Snapshot =
        LaunchCheck.Snapshot(
            notifications = readiness.notifications,
            exactAlarms = readiness.exactAlarms,
            fullScreen = readiness.fullScreen,
            batteryUnrestricted = batteryUnrestricted,
            confirmedAt = SwipeTest.setupConfirmedAt(getApplication()),
            verdict = swipeVerdict,
            pendingAt = swipePendingAt,
            snoozedUntil = LaunchCheck.snoozedUntil(getApplication()),
            now = now
        )

    /**
     * Runs the check on every foreground, but only *acts* on a few of them.
     *
     * The popup is one shot per cold start on purpose: re-opening a dialog every
     * time the user comes back from another app would be indistinguishable from
     * a nag and would train them to dismiss it without reading. So the full
     * decision is taken once, and later foregrounds only re-read the state —
     * with two exceptions, both of which mean the situation changed under the
     * user's feet rather than merely being unaddressed:
     *
     *  - they just came back from the settings screen we sent them to, so the
     *    question "三项都打开了吗" is now answerable;
     *  - a measurement came back as a miss, which is the app learning something
     *    new, not repeating itself.
     */
    fun onForeground() {
        refreshReadiness()
        val snapshot = launchSnapshot()
        launchState = snapshot
        val first = !sawForeground
        sawForeground = true

        if (leftForSettings) {
            leftForSettings = false
            // Nothing left to ask when the switches are already confirmed.
            if (LaunchCheck.manualState(snapshot) != LaunchCheck.State.CONFIRMED) {
                launchDialog = false
                launchReturnConfirm = true
                return
            }
        }

        val failed = LaunchCheck.Reason.MEASUREMENT_FAILED in LaunchCheck.reasons(snapshot)
        launchDialog = when {
            first -> LaunchCheck.shouldPrompt(snapshot)
            failed -> true
            else -> false
        }
    }

    fun dismissLaunchDialog() {
        launchDialog = false
    }

    /** Re-opens the check from the home banner, after a dismissal. */
    fun showLaunchCheck() {
        launchState = launchSnapshot()
        launchDialog = true
    }

    /** "以后再说" — quiet for a day, unless a measurement proves otherwise. */
    fun snoozeLaunchCheck() {
        LaunchCheck.snooze(getApplication())
        launchDialog = false
        launchState = launchSnapshot()
    }

    /**
     * Opens the one page that holds the three switches and remembers that we did,
     * so the next foreground asks whether it worked.
     */
    fun openManualSwitches(): Boolean {
        leftForSettings = true
        return OemAutostart.openForManualSwitches(getApplication())
    }

    /** "再去看看" — reopened the page, so ask again when they come back. */
    fun reopenManualSwitches() {
        launchReturnConfirm = false
        openManualSwitches()
    }

    /**
     * The user says all three are on.
     *
     * Their word is recorded — but not believed. Recording it only opens a
     * window in which the phone can prove it, by arming a real ring 60 seconds
     * out. Until that ring lands the switches stay "待确认", and if it never
     * lands they fall back to "需要你处理" rather than sitting on a claim nobody
     * checked.
     *
     * @return the reminder used for the measurement, or null when there is
     *   nothing enabled to ring with — the switches then stay 待确认, which is
     *   the honest state.
     */
    fun confirmManualSwitches(): Alarm? {
        LaunchCheck.clearSnooze(getApplication())
        SwipeTest.markSetupConfirmed(getApplication())
        launchReturnConfirm = false
        refreshReadiness()
        launchState = launchSnapshot()
        return startSwipeTest()
    }

    fun dismissReturnConfirm() {
        launchReturnConfirm = false
    }

    /** Opens the OEM autostart screen, falling back to app info. */
    fun openAutostart(): Boolean = OemAutostart.open(getApplication())

    fun openBatterySettings(): Boolean = OemAutostart.openBattery(getApplication())

    fun openAppInfo(): Boolean = OemAutostart.openAppInfo(getApplication())

    fun upcoming(limit: Int = 3): List<Pair<Alarm, java.time.ZonedDateTime>> {
        val now = java.time.ZonedDateTime.now()
        return alarms.asSequence()
            .filter { it.enabled }
            .flatMap { alarm ->
                NextOccurrence.nextN(alarm, now, limit).asSequence().map { alarm to it }
            }
            .sortedBy { it.second }
            .take(limit)
            .toList()
    }

    private fun checkReadiness(): SystemReadiness {
        val context = getApplication<Application>()
        val notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

        val fullScreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        } else {
            true
        }
        return SystemReadiness(notifications, exact, fullScreen)
    }
}
