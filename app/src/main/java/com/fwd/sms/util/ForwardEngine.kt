package com.fwd.sms.util

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import com.fwd.sms.data.*
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import javax.net.ssl.HttpsURLConnection

/** Motor de reenvio — avalia regras e despacha para cada destino */
object ForwardEngine {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun process(context: Context, sender: String, body: String, source: SourceType) {
        if (!RuleStore.isGlobalEnabled(context)) return

        val rules = RuleStore.loadRules(context)
        for (rule in rules) {
            if (!rule.enabled) continue
            if (rule.source != SourceType.ALL && rule.source != source) continue
            if (!matchesFilter(sender, body, rule.filter)) continue

            // body limpo — apenas o corpo da mensagem, sem prefixos
            for (dest in rule.destinations) {
                scope.launch {
                    try {
                        dispatch(context, dest, body, rule)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun matchesFilter(sender: String, body: String, f: MessageFilter): Boolean {
        if (f.senderContains.isNotBlank()) {
            val terms = f.senderContains.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (terms.isNotEmpty()) {
                val senderDigits = sender.filter { it.isDigit() }.takeLast(9)
                val match = terms.any { term ->
                    val termDigits = term.filter { it.isDigit() }.takeLast(9)
                    if (termDigits.length >= 4) {
                        termDigits == senderDigits
                    } else {
                        sender.contains(term, ignoreCase = true)
                    }
                }
                if (!match) return false
            }
        }
        if (f.bodyContains.isNotBlank() && !body.contains(f.bodyContains, ignoreCase = true)) return false
        if (f.senderExclude.isNotBlank() && sender.contains(f.senderExclude, ignoreCase = true)) return false
        if (f.bodyExclude.isNotBlank() && body.contains(f.bodyExclude, ignoreCase = true)) return false
        return true
    }

    private fun dispatch(context: Context, dest: Destination, message: String, rule: ForwardRule) {
        when (dest.type) {
            DestinationType.SMS -> sendSms(context, dest.value, message, rule.simSlot)
            DestinationType.EMAIL -> sendEmail(context, dest.value, message, rule.name)
            DestinationType.TELEGRAM -> sendTelegram(context, dest.value, message)
            DestinationType.WEBHOOK -> sendWebhook(dest.value, message)
        }
    }

    private fun sendSms(context: Context, number: String, text: String, simSlot: Int) {
        val sm: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (simSlot >= 0) {
                context.getSystemService(SmsManager::class.java).createForSubscriptionId(simSlot)
            } else {
                context.getSystemService(SmsManager::class.java)
            }
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        val parts = sm.divideMessage(text)
        sm.sendMultipartTextMessage(number, null, parts, null, null)
    }

    private fun sendEmail(context: Context, to: String, body: String, subject: String) {
        val cfg = RuleStore.loadSmtp(context)
        if (cfg.host.isBlank()) return

        val props = Properties().apply {
            put("mail.smtp.host", cfg.host)
            put("mail.smtp.port", cfg.port.toString())
            put("mail.smtp.auth", "true")
            if (cfg.useTls) put("mail.smtp.starttls.enable", "true")
        }

        val session = javax.mail.Session.getInstance(props, object : javax.mail.Authenticator() {
            override fun getPasswordAuthentication() =
                javax.mail.PasswordAuthentication(cfg.username, cfg.password)
        })

        val msg = javax.mail.internet.MimeMessage(session).apply {
            setFrom(javax.mail.internet.InternetAddress(cfg.fromAddress))
            setRecipient(javax.mail.Message.RecipientType.TO, javax.mail.internet.InternetAddress(to))
            setSubject("SMS Forward: $subject")
            setText(body)
        }
        javax.mail.Transport.send(msg)
    }

    private fun sendTelegram(context: Context, chatId: String, text: String) {
        val cfg = RuleStore.loadTelegram(context)
        if (cfg.botToken.isBlank()) return
        val targetChat = chatId.ifBlank { cfg.defaultChatId }
        if (targetChat.isBlank()) return

        val url = URL("https://api.telegram.org/bot${cfg.botToken}/sendMessage")
        val conn = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            doOutput = true
        }
        val payload = """{"chat_id":"$targetChat","text":"${text.replace("\"", "\\\"").replace("\n", "\\n")}"}"""
        conn.outputStream.use { it.write(payload.toByteArray()) }
        conn.responseCode
        conn.disconnect()
    }

    private fun sendWebhook(webhookUrl: String, text: String) {
        val url = URL(webhookUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            doOutput = true
        }
        val payload = """{"message":"${text.replace("\"", "\\\"").replace("\n", "\\n")}"}"""
        conn.outputStream.use { it.write(payload.toByteArray()) }
        conn.responseCode
        conn.disconnect()
    }
}
