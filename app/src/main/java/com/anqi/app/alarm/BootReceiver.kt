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
        AlarmNotifier.ensureChannel(context)
        // A reboot clears every registration, so nothing can be "late by the
        // clock" here in a way that is safe to replay: SelfHeal still audits the
        // ledger, but the recovery window is what keeps a week-old slot from
        // suddenly ringing at boot.
        val late = SelfHeal.heal(context, ensureGuard = true)
        Diag.log(
            context,
            "BOOT 重排",
            "$action · ${AlarmStore(context).all().size} 个提醒" +
                if (late > 0) "，补响 $late 个" else ""
        )
    }
}
