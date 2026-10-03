package com.fwd.sms.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.fwd.sms.data.SourceType
import com.fwd.sms.util.ForwardEngine

/**
 * Captura notificacoes de apps de mensagens para apanhar RCS e mensagens de apps.
 * O utilizador tem de dar permissao em Settings > Notification access.
 */
class NotificationListener : NotificationListenerService() {

    // apps de mensagens conhecidos
    private val messagingApps = setOf(
        "com.google.android.apps.messaging",   // Google Messages
        "com.samsung.android.messaging",        // Samsung Messages
        "com.android.mms",                      // AOSP Messages
        "com.whatsapp",                         // WhatsApp
        "org.telegram.messenger",               // Telegram
        "com.facebook.orca",                    // Messenger
        "com.viber.voip",                       // Viber
        "com.instagram.android",                // Instagram DMs
        "com.Slack",                            // Slack
        "com.microsoft.teams"                   // Teams
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in messagingApps) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return

        val sender = "[${getAppLabel(sbn.packageName)}] $title"

        ForwardEngine.process(applicationContext, sender, text, SourceType.NOTIFICATION)
    }

    private fun getAppLabel(pkg: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            pkg.substringAfterLast(".")
        }
    }
}
