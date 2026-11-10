package com.anqi.app

import android.app.Application
import com.anqi.app.alarm.AlarmNotifier
import com.anqi.app.alarm.Diag
import com.anqi.app.alarm.GuardService
import com.anqi.app.alarm.SelfHeal
import com.anqi.app.alarm.SwipeTest
import com.anqi.app.data.AlarmStore

class AnqiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AlarmNotifier.ensureChannel(this)
        Diag.log(this, "APP 进程启动")

        // Bumped before anything else looks at it: the swipe test can only tell
        // "the ring survived a swipe" from "nothing was swiped" by noticing that
        // the process was torn down and came back.
        SwipeTest.noteProcessStart(this)

        // Four independent layers, because each one alone has already been
        // proven insufficient on a real device:
        //  1. every process start repairs the queue *and* replays a slot that was
        //     silently dropped (a force-stop kills the process, so the next start
        //     is the first chance to notice the silence),
        //  2. a foreground guard keeps the process — and therefore the
        //     registrations — alive while the user expects to be reminded,
        //  3. a six-hour heartbeat wakes a dead process up again,
        //  4. a persisted JobScheduler job is a different subsystem entirely, so a
        //     sweep of AlarmManager cannot take it down too.
        runCatching { SelfHeal.heal(this, ensureGuard = false) }
        if (AlarmStore(this).all().any { it.enabled }) {
            runCatching { GuardService.start(this) }
        }
    }
}
