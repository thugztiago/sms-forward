package com.fwd.sms.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object RuleStore {

    private const val PREF = "sms_fwd_rules"
    private const val KEY_RULES = "rules"
    private const val KEY_GLOBAL_ENABLED = "global_enabled"
    private const val KEY_SMTP = "smtp_config"
    private const val KEY_TELEGRAM = "telegram_config"
    private val gson = Gson()

    fun loadRules(ctx: Context): List<ForwardRule> {
        val json = prefs(ctx).getString(KEY_RULES, null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<ForwardRule>>() {}.type)
        } catch (_: Exception) { emptyList() }
    }

    fun saveRules(ctx: Context, rules: List<ForwardRule>) {
        prefs(ctx).edit().putString(KEY_RULES, gson.toJson(rules)).apply()
    }

    fun addRule(ctx: Context, rule: ForwardRule) {
        saveRules(ctx, loadRules(ctx) + rule)
    }

    fun updateRule(ctx: Context, rule: ForwardRule) {
        saveRules(ctx, loadRules(ctx).map { if (it.id == rule.id) rule else it })
    }

    fun deleteRule(ctx: Context, ruleId: String) {
        saveRules(ctx, loadRules(ctx).filter { it.id != ruleId })
    }

    fun isGlobalEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_GLOBAL_ENABLED, false)

    fun setGlobalEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_GLOBAL_ENABLED, enabled).apply()
    }

    fun loadSmtp(ctx: Context): SmtpConfig {
        val json = prefs(ctx).getString(KEY_SMTP, null) ?: return SmtpConfig()
        return try { gson.fromJson(json, SmtpConfig::class.java) } catch (_: Exception) { SmtpConfig() }
    }

    fun saveSmtp(ctx: Context, cfg: SmtpConfig) {
        prefs(ctx).edit().putString(KEY_SMTP, gson.toJson(cfg)).apply()
    }

    fun loadTelegram(ctx: Context): TelegramConfig {
        val json = prefs(ctx).getString(KEY_TELEGRAM, null) ?: return TelegramConfig()
        return try { gson.fromJson(json, TelegramConfig::class.java) } catch (_: Exception) { TelegramConfig() }
    }

    fun saveTelegram(ctx: Context, cfg: TelegramConfig) {
        prefs(ctx).edit().putString(KEY_TELEGRAM, gson.toJson(cfg)).apply()
    }

    fun exportAll(ctx: Context): String {
        return gson.toJson(mapOf(
            "rules" to loadRules(ctx),
            "smtp" to loadSmtp(ctx),
            "telegram" to loadTelegram(ctx),
            "global_enabled" to isGlobalEnabled(ctx)
        ))
    }

    fun importAll(ctx: Context, json: String) {
        try {
            val map: Map<String, Any> = gson.fromJson(json, object : TypeToken<Map<String, Any>>() {}.type)
            saveRules(ctx, gson.fromJson(gson.toJson(map["rules"]), object : TypeToken<List<ForwardRule>>() {}.type))
            saveSmtp(ctx, gson.fromJson(gson.toJson(map["smtp"]), SmtpConfig::class.java))
            saveTelegram(ctx, gson.fromJson(gson.toJson(map["telegram"]), TelegramConfig::class.java))
            setGlobalEnabled(ctx, (map["global_enabled"] as? Boolean) ?: false)
        } catch (_: Exception) { }
    }

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}

data class SmtpConfig(
    val host: String = "",
    val port: Int = 587,
    val username: String = "",
    val password: String = "",
    val fromAddress: String = "",
    val useTls: Boolean = true
)

data class TelegramConfig(
    val botToken: String = "",
    val defaultChatId: String = ""
)
