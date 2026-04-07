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
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class EditBotActivity : AppCompatActivity() {
    private lateinit var rootScroll: NestedScrollView
    private lateinit var editDisplayName: TextInputEditText
    private lateinit var editPersona: TextInputEditText
    private lateinit var textModelStatus: TextView
    private lateinit var btnDownloadModel: Button
    private lateinit var progressModelDownload: ProgressBar
    private lateinit var textDownloadProgress: TextView
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
        textModelStatus = findViewById(R.id.text_model_status)
        btnDownloadModel = findViewById(R.id.btn_download_model)
        progressModelDownload = findViewById(R.id.progress_model_download)
        textDownloadProgress = findViewById(R.id.text_download_progress)
        spinnerReplyMode = findViewById(R.id.spinner_reply_mode)
        spinnerTriggerMode = findViewById(R.id.spinner_trigger_mode)
        btnSave = findViewById(R.id.btn_save)

        configureSpinner(spinnerReplyMode, replyModes)
        configureSpinner(spinnerTriggerMode, triggerModes)
        rootScroll.bindFocusScroll(editDisplayName, editPersona)

        bindCurrentConfig()
        updateModelStatus()

        btnDownloadModel.setOnClickListener {
            if (LlmModelManager.hasModel(this)) {
                Toast.makeText(this, "이미 모델이 설치되어 있습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            downloadModel()
        }
    }

    private fun updateModelStatus() {
        val info = LlmModelManager.getModelInfo(this)
        if (info.exists) {
            textModelStatus.text = "✅ LLM 모델 준비됨 (${info.sizeMb}MB)"
            textModelStatus.setTextColor(getColor(R.color.colorSuccess))
            btnDownloadModel.text = "모델 재다운로드"
        } else {
            textModelStatus.text = "⚠️ LLM 모델이 필요합니다"
            textModelStatus.setTextColor(getColor(R.color.colorDanger))
            btnDownloadModel.text = "모델 다운로드 (~1.1GB)"
        }
    }

    private fun downloadModel() {
        lifecycleScope.launch {
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
