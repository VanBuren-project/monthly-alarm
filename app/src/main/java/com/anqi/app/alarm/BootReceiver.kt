package com.anqi.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.anqi.app.data.AlarmStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED
        ) {
            return
        }
        val store = AlarmStore(context)
        AlarmNotifier.ensureChannel(context)
        val scheduler = AlarmScheduler(context)
        scheduler.rescheduleAll(store.all())
        // A reboot clears every repeating registration, heartbeat included.
        scheduler.scheduleHeartbeat()
    }
}
