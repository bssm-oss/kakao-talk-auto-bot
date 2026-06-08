package com.example.kakaotalkautobot

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class EditBotActivity : AppCompatActivity() {
    private lateinit var rootScroll: NestedScrollView
    private lateinit var editDisplayName: TextInputEditText
    private lateinit var editPersona: TextInputEditText
    private lateinit var editPersonaExamples: TextInputEditText
    private lateinit var switchLearnedUserStyle: SwitchMaterial
    private lateinit var learnedUserStylePreview: TextView
    private lateinit var editLearnedUserStyle: TextInputEditText
    private lateinit var resetLearnedUserStyleButton: MaterialButton
    private lateinit var textModelStatus: TextView
    private lateinit var btnDownloadModel: Button
    private lateinit var progressModelDownload: ProgressBar
    private lateinit var textDownloadProgress: TextView
    private lateinit var textGroundingSummary: TextView
    private lateinit var textProviderSummary: TextView
    private lateinit var spinnerReplyMode: Spinner
    private lateinit var spinnerTriggerMode: Spinner
    private lateinit var btnSave: Button

    private val replyModes = listOf("간결하게", "균형 있게", "조금 더 자세히")
    private val triggerModes = listOf("AI가 판단", "호출어/멘션만", "질문/명령만", "모든 메시지")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_bot)

        rootScroll = findViewById(R.id.edit_bot_scroll)
        editDisplayName = findViewById(R.id.edit_display_name)
        editPersona = findViewById(R.id.edit_persona)
        editPersonaExamples = findViewById(R.id.edit_persona_examples)
        switchLearnedUserStyle = findViewById(R.id.switch_learned_user_style)
        learnedUserStylePreview = findViewById(R.id.text_learned_user_style_preview)
        editLearnedUserStyle = findViewById(R.id.edit_learned_user_style)
        resetLearnedUserStyleButton = findViewById(R.id.btn_reset_learned_user_style)
        textModelStatus = findViewById(R.id.text_model_status)
        btnDownloadModel = findViewById(R.id.btn_download_model)
        progressModelDownload = findViewById(R.id.progress_model_download)
        textDownloadProgress = findViewById(R.id.text_download_progress)
        textGroundingSummary = findViewById(R.id.text_grounding_summary)
        textProviderSummary = findViewById(R.id.text_provider_summary)
        spinnerReplyMode = findViewById(R.id.spinner_reply_mode)
        spinnerTriggerMode = findViewById(R.id.spinner_trigger_mode)
        btnSave = findViewById(R.id.btn_save)

        configureSpinner(spinnerReplyMode, replyModes)
        configureSpinner(spinnerTriggerMode, triggerModes)
        rootScroll.bindFocusScroll(editDisplayName, editPersona, editPersonaExamples, editLearnedUserStyle)

        bindCurrentConfig()
        updateModelStatus()
        updateProviderSummary()

        btnDownloadModel.setOnClickListener {
            if (!LlmEngine.isRuntimeSupportedOnCurrentDevice()) {
                Toast.makeText(this, "에뮬레이터에서는 로컬 모델 실행을 지원하지 않습니다. ARM64 실기기에서 설정해 주세요.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (LlmModelManager.hasModel(this, LlmModelManager.DEFAULT_MODEL)) {
                Toast.makeText(this, "이미 모델이 설치되어 있습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            downloadModel()
        }

        btnSave.setOnClickListener {
            saveConfig()
        }

        resetLearnedUserStyleButton.setOnClickListener {
            StyleProfileStore.resetUserLearnedStyle(this)
            editLearnedUserStyle.setText("")
            bindLearnedUserStyle()
            Toast.makeText(this, "학습된 내 말투 수정을 초기화했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateModelStatus() {
        val info = LlmModelManager.getModelInfo(this, LlmModelManager.DEFAULT_MODEL)
        if (!LlmEngine.isRuntimeSupportedOnCurrentDevice()) {
            textModelStatus.text = "⚠️ 에뮬레이터에서는 Gemma/LiteRT-LM 실행이 지원되지 않습니다. ARM64 실기기에서 사용해 주세요."
            textModelStatus.setTextColor(getColor(R.color.colorWarning))
            btnDownloadModel.text = "실기기에서 다운로드 가능"
            btnDownloadModel.isEnabled = false
        } else if (info.matchesExpectedSource) {
            val checksum = if (info.checksumVerified) " · 해시 검증됨" else " · 해시 검증 대기"
            textModelStatus.text = "✅ Gemma 4 준비됨 (${info.sizeMb}MB$checksum)"
            textModelStatus.setTextColor(getColor(R.color.colorSuccess))
            btnDownloadModel.text = "모델 재다운로드"
            btnDownloadModel.isEnabled = true
        } else if (info.exists) {
            textModelStatus.text = "⚠️ 모델 검증 실패 (${info.sizeMb}MB) · ${info.validationMessage} · 기본 모델 다운로드 필요"
            textModelStatus.setTextColor(getColor(R.color.colorWarning))
            btnDownloadModel.text = "Gemma 4 다운로드 (~2.58GB)"
            btnDownloadModel.isEnabled = true
        } else {
            textModelStatus.text = "⚠️ Gemma 4 모델이 필요합니다"
            textModelStatus.setTextColor(getColor(R.color.colorDanger))
            btnDownloadModel.text = "Gemma 4 다운로드 (~2.58GB)"
            btnDownloadModel.isEnabled = true
        }
    }

    private fun updateProviderSummary() {
        textProviderSummary.text = "Gemma 4 로컬 모델만 사용합니다. 모델 실행은 ARM64 실기기 기준입니다."
        textGroundingSummary.text = "현재 메시지, 사용자 예시, 방별 말투, 최근 대화, 방 메모, 자동 메모리 요약을 함께 참고해 답장합니다."
    }

    private fun downloadModel() {
        lifecycleScope.launch {
            if (!LlmEngine.isRuntimeSupportedOnCurrentDevice()) {
                Toast.makeText(this@EditBotActivity, "에뮬레이터에서는 다운로드를 막았습니다. 실기기에서 진행해 주세요.", Toast.LENGTH_LONG).show()
                updateModelStatus()
                return@launch
            }
            btnDownloadModel.isEnabled = false
            progressModelDownload.visibility = View.VISIBLE
            textDownloadProgress.visibility = View.VISIBLE

            LlmModelManager.downloadModel(
                this@EditBotActivity,
                onProgress = { progress ->
                    progressModelDownload.progress = progress
                    textDownloadProgress.text = "다운로드 중... $progress%"
                }
            ).onSuccess { file ->
                progressModelDownload.visibility = View.GONE
                textDownloadProgress.visibility = View.GONE
                textDownloadProgress.text = ""
                updateModelStatus()
                btnDownloadModel.isEnabled = true
                Toast.makeText(
                    this@EditBotActivity,
                    "모델이 다운로드되었습니다. (${file.length() / 1024 / 1024}MB)",
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure { error ->
                progressModelDownload.visibility = View.GONE
                textDownloadProgress.visibility = View.GONE
                textDownloadProgress.text = ""
                updateModelStatus()
                btnDownloadModel.isEnabled = true
                Toast.makeText(
                    this@EditBotActivity,
                    "모델 다운로드 실패: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun bindCurrentConfig() {
        val config = AppSettings.getAiConfig(this)
        editDisplayName.setText(config.displayName)
        editPersona.setText(config.persona)
        editPersonaExamples.setText(config.personaExamples)
        spinnerReplyMode.setSelection(indexOrZero(replyModes, config.replyMode))
        spinnerTriggerMode.setSelection(indexOrZero(triggerModes, config.triggerMode))
        bindLearnedUserStyle()
    }

    private fun saveConfig() {
        AppSettings.saveAiConfig(
            this,
            AppSettings.AiConfig(
                displayName = editDisplayName.text?.toString()?.trim().orEmpty().ifBlank { "나" },
                persona = editPersona.text?.toString()?.trim().orEmpty().ifBlank { "친절하고 짧게 핵심만 답장합니다." },
                personaExamples = editPersonaExamples.text?.toString()?.trim().orEmpty(),
                provider = "",
                providerType = "llm",
                providerModel = "gemma-4-e2b-it-litertlm",
                apiKeyMode = "불필요",
                apiKey = "",
                replyMode = spinnerReplyMode.selectedItem?.toString().orEmpty().ifBlank { replyModes.first() },
                triggerMode = spinnerTriggerMode.selectedItem?.toString().orEmpty().ifBlank { triggerModes.first() }
            )
        )
        StyleProfileStore.setUserLearnedStyleEnabled(this, switchLearnedUserStyle.isChecked)
        StyleProfileStore.saveUserLearnedStyleOverride(this, editLearnedUserStyle.text?.toString().orEmpty())
        updateModelStatus()
        updateProviderSummary()
        Toast.makeText(this, "응답 설정을 저장했습니다.", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun bindLearnedUserStyle() {
        val config = AppSettings.getAiConfig(this)
        val sourceRoom = AppSettings.getMostRecentImportedRoom(this)?.name
        val history = sourceRoom
            ?.let { RoomStore.recentMessages(this, it, limit = 40) }
            .orEmpty()
        val importedText = sourceRoom
            ?.let { AppSettings.getRoomMemory(this, it) }
            .orEmpty()
        val state = StyleProfileStore.getUserLearnedStyleState(this, config.displayName, history, importedText)
        switchLearnedUserStyle.isChecked = state.enabled
        editLearnedUserStyle.setText(state.override)
        resetLearnedUserStyleButton.text = state.resetOverrideButtonLabel
        resetLearnedUserStyleButton.isEnabled = state.hasManualOverride
        learnedUserStylePreview.text = StyleProfileStore.learnedStylePreviewText(
            subject = "내 말투",
            state = state,
            emptyMessage = "자동 추출된 내 말투가 아직 없습니다. 방 대화나 CSV 내 발화가 쌓이면 답장 때 자동으로 참고합니다."
        )
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
