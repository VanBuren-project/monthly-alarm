package com.anqi.app

import android.app.Application
import com.anqi.app.alarm.AlarmNotifier

class AnqiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AlarmNotifier.ensureChannel(this)
    }
}
