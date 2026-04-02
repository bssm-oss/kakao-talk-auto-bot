package com.example.kakaotalkautobot

import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.google.android.material.textfield.TextInputEditText

class EditBotActivity : AppCompatActivity() {
    private lateinit var rootScroll: NestedScrollView
    private lateinit var editDisplayName: TextInputEditText
    private lateinit var editPersona: TextInputEditText
    private lateinit var spinnerProvider: Spinner
    private lateinit var spinnerApiKeyMode: Spinner
    private lateinit var editApiKey: TextInputEditText
    private lateinit var spinnerReplyMode: Spinner
    private lateinit var spinnerTriggerMode: Spinner
    private lateinit var btnSave: Button

    private val providers = listOf("OpenAI", "OpenRouter", "Anthropic", "Gemini", "Custom")
    private val apiKeyModes = listOf("앱에 저장", "직접 입력", "외부에서 관리")
    private val replyModes = listOf("간결하게", "균형 있게", "조금 더 자세히")
    private val triggerModes = listOf("AI가 판단", "호출어/멘션만", "질문/명령만", "모든 메시지")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_bot)

        rootScroll = findViewById(R.id.edit_bot_scroll)
        editDisplayName = findViewById(R.id.edit_display_name)
        editPersona = findViewById(R.id.edit_persona)
        spinnerProvider = findViewById(R.id.spinner_provider)
        spinnerApiKeyMode = findViewById(R.id.spinner_api_key_mode)
        editApiKey = findViewById(R.id.edit_api_key)
        spinnerReplyMode = findViewById(R.id.spinner_reply_mode)
        spinnerTriggerMode = findViewById(R.id.spinner_trigger_mode)
        btnSave = findViewById(R.id.btn_save)

        configureSpinner(spinnerProvider, providers)
        configureSpinner(spinnerApiKeyMode, apiKeyModes)
        configureSpinner(spinnerReplyMode, replyModes)
        configureSpinner(spinnerTriggerMode, triggerModes)
        rootScroll.bindFocusScroll(editDisplayName, editPersona, editApiKey)

        spinnerApiKeyMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val mode = apiKeyModes[position]
                val enabled = mode != "외부에서 관리"
                editApiKey.isEnabled = enabled
                editApiKey.alpha = if (enabled) 1f else 0.5f
                if (!enabled) {
                    editApiKey.setText("")
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        bindCurrentConfig()

        btnSave.setOnClickListener {
            val displayName = editDisplayName.text?.toString()?.trim().orEmpty()
            val persona = editPersona.text?.toString()?.trim().orEmpty()
            val apiKeyMode = spinnerApiKeyMode.selectedItem?.toString().orEmpty()
            val apiKey = if (apiKeyMode == "외부에서 관리") "" else editApiKey.text?.toString()?.trim().orEmpty()

            if (displayName.isEmpty()) {
                Toast.makeText(this, "내 이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (persona.isEmpty()) {
                Toast.makeText(this, "페르소나를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AppSettings.saveAiConfig(
                this,
                AppSettings.AiConfig(
                    displayName = displayName,
                    persona = persona,
                    provider = spinnerProvider.selectedItem?.toString().orEmpty(),
                    apiKeyMode = apiKeyMode,
                    apiKey = apiKey,
                    replyMode = spinnerReplyMode.selectedItem?.toString().orEmpty(),
                    triggerMode = spinnerTriggerMode.selectedItem?.toString().orEmpty()
                )
            )

            Toast.makeText(this, "AI 답장 설정을 저장했습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun bindCurrentConfig() {
        val config = AppSettings.getAiConfig(this)
        editDisplayName.setText(config.displayName)
        editPersona.setText(config.persona)
        editApiKey.setText(config.apiKey)
        spinnerProvider.setSelection(indexOrZero(providers, config.provider))
        spinnerApiKeyMode.setSelection(indexOrZero(apiKeyModes, config.apiKeyMode))
        spinnerReplyMode.setSelection(indexOrZero(replyModes, config.replyMode))
        spinnerTriggerMode.setSelection(indexOrZero(triggerModes, config.triggerMode))
    }

    private fun configureSpinner(spinner: Spinner, items: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun indexOrZero(items: List<String>, value: String): Int {
        val index = items.indexOf(value)
        return if (index >= 0) index else 0
    }
}
