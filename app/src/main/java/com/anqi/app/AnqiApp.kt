package com.anqi.app

import android.app.Application
import com.anqi.app.alarm.AlarmNotifier
import com.anqi.app.alarm.AlarmScheduler
import com.anqi.app.data.AlarmStore

class AnqiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AlarmNotifier.ensureChannel(this)

        // Every process start re-arms the queue and the heartbeat. Nothing else
        // in the app does this on a cold start, so without it a single failed
        // registration (ROM "deep clean", force-stop, restored backup) stayed
        // failed forever and the reminder simply never rang again.
        val scheduler = AlarmScheduler(this)
        runCatching { scheduler.rescheduleAll(AlarmStore(this).all()) }
        runCatching { scheduler.scheduleHeartbeat() }
    }
}
