package com.anqi.app.viewmodel

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
