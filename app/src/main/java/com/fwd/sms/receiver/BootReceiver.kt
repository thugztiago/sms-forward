package com.fwd.sms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fwd.sms.data.RuleStore
import com.fwd.sms.service.ForegroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (RuleStore.isGlobalEnabled(context)) {
                ForegroundService.start(context)
            }
        }
    }
}
