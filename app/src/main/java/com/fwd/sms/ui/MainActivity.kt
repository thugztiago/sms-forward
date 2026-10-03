package com.fwd.sms.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fwd.sms.R
import com.fwd.sms.data.RuleStore
import com.fwd.sms.data.ForwardRule
import com.fwd.sms.databinding.ActivityMainBinding
import com.fwd.sms.service.ForegroundService
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filter { !it.value }.keys
        if (denied.isNotEmpty()) {
            Toast.makeText(this, "Permissões necessárias: $denied", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        requestPermissions()

        b.switchGlobal.isChecked = RuleStore.isGlobalEnabled(this)
        b.switchGlobal.setOnCheckedChangeListener { _, checked ->
            RuleStore.setGlobalEnabled(this, checked)
            if (checked) {
                ForegroundService.start(this)
            } else {
                ForegroundService.stop(this)
            }
        }

        // iniciar servico se ja estava ativo
        if (RuleStore.isGlobalEnabled(this)) {
            ForegroundService.start(this)
        }
    }

    override fun onResume() {
        super.onResume()
        showRules()
    }

    private fun showRules() {
        b.content.removeAllViews()

        val rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        rv.adapter = RuleAdapter(RuleStore.loadRules(this))
        b.content.addView(rv)

        val fab = FloatingActionButton(this).apply {
            setImageResource(android.R.drawable.ic_input_add)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, 48, 48)
            }
            setOnClickListener {
                startActivity(Intent(this@MainActivity, RuleEditorActivity::class.java))
            }
        }
        b.content.addView(fab)
    }

    inner class RuleAdapter(private val rules: List<ForwardRule>) :
        RecyclerView.Adapter<RuleAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.tvRuleName)
            val info: TextView = view.findViewById(R.id.tvRuleInfo)
            val switch: MaterialSwitch = view.findViewById(R.id.switchRule)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_rule, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val rule = rules[position]
            holder.name.text = rule.name.ifBlank { "Regra ${position + 1}" }
            val destCount = rule.destinations.size
            val types = rule.destinations.map { it.type.name }.toSet().joinToString(", ")
            holder.info.text = "$destCount destino(s) · $types · ${rule.source.name}"
            holder.switch.isChecked = rule.enabled
            holder.switch.setOnCheckedChangeListener { _, checked ->
                RuleStore.updateRule(this@MainActivity, rule.copy(enabled = checked))
            }
            holder.itemView.setOnClickListener {
                val intent = Intent(this@MainActivity, RuleEditorActivity::class.java)
                intent.putExtra("rule_id", rule.id)
                startActivity(intent)
            }
            holder.itemView.setOnLongClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Apagar regra?")
                    .setMessage(rule.name)
                    .setPositiveButton("Apagar") { _, _ ->
                        RuleStore.deleteRule(this@MainActivity, rule.id)
                        showRules()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
                true
            }
        }

        override fun getItemCount() = rules.size
    }

    private fun requestPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permLauncher.launch(needed.toTypedArray())
        }
    }
}
