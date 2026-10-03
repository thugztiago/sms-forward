package com.fwd.sms.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.fwd.sms.R
import com.fwd.sms.data.*
import com.fwd.sms.databinding.ActivityRuleEditorBinding

class RuleEditorActivity : AppCompatActivity() {

    private lateinit var b: ActivityRuleEditorBinding
    private val destinations = mutableListOf<Destination>()
    private var editingRule: ForwardRule? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRuleEditorBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.toolbarEditor.setNavigationOnClickListener { finish() }

        val ruleId = intent.getStringExtra("rule_id")
        if (ruleId != null) {
            editingRule = RuleStore.loadRules(this).find { it.id == ruleId }
            editingRule?.let { load(it) }
            b.toolbarEditor.title = "Editar Regra"
        }

        b.btnAddDest.setOnClickListener { showAddDestDialog() }
        b.btnSave.setOnClickListener { save() }
    }

    private fun load(rule: ForwardRule) {
        b.etRuleName.setText(rule.name)

        when (rule.source) {
            SourceType.SMS -> b.rbSms.isChecked = true
            SourceType.NOTIFICATION -> b.rbNotif.isChecked = true
            SourceType.ALL -> b.rbAll.isChecked = true
        }

        b.etFilterSender.setText(rule.filter.senderContains)
        b.etFilterBody.setText(rule.filter.bodyContains)
        b.etExcludeSender.setText(rule.filter.senderExclude)
        b.etExcludeBody.setText(rule.filter.bodyExclude)

        destinations.clear()
        destinations.addAll(rule.destinations)
        refreshDestinations()
    }

    private fun showAddDestDialog() {
        val types = DestinationType.values()
        val names = types.map { it.name }

        AlertDialog.Builder(this)
            .setTitle("Tipo de destino")
            .setItems(names.toTypedArray()) { _, which ->
                val type = types[which]
                val hint = when (type) {
                    DestinationType.SMS -> "Número de telefone"
                    DestinationType.EMAIL -> "Email de destino"
                    DestinationType.TELEGRAM -> "Chat ID"
                    DestinationType.WEBHOOK -> "URL (https://...)"
                }
                showValueDialog(type, hint)
            }
            .show()
    }

    private fun showValueDialog(type: DestinationType, hint: String) {
        val et = EditText(this).apply {
            this.hint = hint
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle("${type.name} — Destino")
            .setView(et)
            .setPositiveButton("Adicionar") { _, _ ->
                val value = et.text.toString().trim()
                if (value.isNotEmpty()) {
                    destinations.add(Destination(type, value))
                    refreshDestinations()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun refreshDestinations() {
        b.llDestinations.removeAllViews()
        for ((i, dest) in destinations.withIndex()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 4 }
            }

            row.addView(TextView(this).apply {
                text = "${dest.type.name}: ${dest.value}"
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })

            row.addView(ImageButton(this).apply {
                setImageResource(android.R.drawable.ic_delete)
                setBackgroundResource(android.R.color.transparent)
                setOnClickListener {
                    destinations.removeAt(i)
                    refreshDestinations()
                }
            })

            b.llDestinations.addView(row)
        }
    }

    private fun save() {
        val source = when {
            b.rbNotif.isChecked -> SourceType.NOTIFICATION
            b.rbAll.isChecked -> SourceType.ALL
            else -> SourceType.SMS
        }

        val rule = ForwardRule(
            id = editingRule?.id ?: java.util.UUID.randomUUID().toString(),
            name = b.etRuleName.text.toString().trim(),
            enabled = editingRule?.enabled ?: true,
            source = source,
            destinations = destinations.toList(),
            filter = MessageFilter(
                senderContains = b.etFilterSender.text.toString().trim(),
                bodyContains = b.etFilterBody.text.toString().trim(),
                senderExclude = b.etExcludeSender.text.toString().trim(),
                bodyExclude = b.etExcludeBody.text.toString().trim()
            )
        )

        if (editingRule != null) {
            RuleStore.updateRule(this, rule)
        } else {
            RuleStore.addRule(this, rule)
        }

        Toast.makeText(this, "Regra guardada!", Toast.LENGTH_SHORT).show()
        finish()
    }
}
