package com.fwd.sms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.fwd.sms.data.SourceType
import com.fwd.sms.util.ForwardEngine

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages[0].originatingAddress ?: return
        val body = StringBuilder()
        for (msg in messages) {
            body.append(msg.messageBody)
        }

        ForwardEngine.process(context, sender, body.toString(), SourceType.SMS)
    }
}
