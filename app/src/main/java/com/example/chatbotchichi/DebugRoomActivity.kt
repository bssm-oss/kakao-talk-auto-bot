package com.example.kakaotalkautobot

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DebugRoomActivity : AppCompatActivity() {
    private lateinit var rootScroll: NestedScrollView
    private lateinit var editRoomName: TextInputEditText
    private lateinit var roomMetaText: TextView
    private lateinit var editMemory: TextInputEditText
    private lateinit var spinnerReplyMode: Spinner
    private lateinit var spinnerTriggerMode: Spinner
    private lateinit var editTriggerValue: TextInputEditText
    private lateinit var editAllowedSenders: TextInputEditText
    private lateinit var editBlockedSenders: TextInputEditText
    private lateinit var editCannedReplies: TextInputEditText
    private lateinit var saveButton: MaterialButton
    private lateinit var clearButton: MaterialButton

    private val replyModes = listOf("AI 답장", "고정 답장")
    private val triggerModes = listOf("AI가 판단", "호출어/멘션만", "질문/명령만", "특정 키워드", "모든 메시지")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_room)

        rootScroll = findViewById(R.id.debug_room_scroll)
        editRoomName = findViewById(R.id.edit_room_name)
        roomMetaText = findViewById(R.id.text_room_meta)
        editMemory = findViewById(R.id.edit_memory)
        spinnerReplyMode = findViewById(R.id.spinner_reply_mode)
        spinnerTriggerMode = findViewById(R.id.spinner_trigger_mode)
        editTriggerValue = findViewById(R.id.edit_trigger_value)
        editAllowedSenders = findViewById(R.id.edit_allowed_senders)
        editBlockedSenders = findViewById(R.id.edit_blocked_senders)
        editCannedReplies = findViewById(R.id.edit_canned_replies)
        saveButton = findViewById(R.id.btn_save_memory)
        clearButton = findViewById(R.id.btn_clear_memory)

        configureSpinner(spinnerReplyMode, replyModes)
        configureSpinner(spinnerTriggerMode, triggerModes)
        rootScroll.bindFocusScroll(
            editRoomName,
            editMemory,
            editTriggerValue,
            editAllowedSenders,
            editBlockedSenders,
            editCannedReplies
        )

        val initialRoomName = intent.getStringExtra("roomName")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: BotManager.getBots(this).firstOrNull { it.name != "기본 자동응답" }?.roomPattern
            ?: ""

        editRoomName.setText(initialRoomName)
        bindRoom(initialRoomName)

        clearButton.setOnClickListener {
            editMemory.setText("")
        }

        saveButton.setOnClickListener {
            val roomName = editRoomName.text?.toString()?.trim().orEmpty()
            if (roomName.isEmpty()) {
                Toast.makeText(this, "방 이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val existingConfig = BotManager.getConfigByRoomPattern(this, roomName)
            val baseConfig = existingConfig
                ?: BotManager.getConfig(this, "기본 자동응답")
                ?: AutoReplyJson.defaultConfig("기본 자동응답")
            val updatedConfig = baseConfig.copy(
                name = if (existingConfig == null || baseConfig.name == "기본 자동응답") roomName else baseConfig.name,
                roomPattern = roomName,
                enabled = true,
                captureEnabled = true,
                replyEnabled = true,
                replyMode = if (spinnerReplyMode.selectedItem == "고정 답장") "canned" else "provider",
                roomMemory = editMemory.text?.toString().orEmpty(),
                allowedSenders = splitLines(editAllowedSenders.text?.toString().orEmpty()),
                blockedSenders = splitLines(editBlockedSenders.text?.toString().orEmpty()),
                cannedReplies = splitLines(editCannedReplies.text?.toString().orEmpty()),
                trigger = TriggerConfig(
                    mode = mapTriggerMode(spinnerTriggerMode.selectedItem?.toString().orEmpty()),
                    value = editTriggerValue.text?.toString().orEmpty().trim()
                ),
                importHistory = ""
            )
            if (updatedConfig.replyMode == "canned" && updatedConfig.cannedReplies.isEmpty()) {
                Toast.makeText(this, "고정 답장을 한 줄 이상 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            BotManager.saveConfig(this, updatedConfig)
            Toast.makeText(this, "${roomName} 메모리를 저장했습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun bindRoom(roomName: String) {
        val config = BotManager.getConfigByRoomPattern(this, roomName)
        editMemory.setText(config?.roomMemory ?: AppSettings.getRoomMemory(this, roomName))
        spinnerReplyMode.setSelection(if (config?.replyMode.equals("canned", true)) 1 else 0)
        spinnerTriggerMode.setSelection(triggerModes.indexOf(readableTriggerMode(config?.trigger?.mode)).coerceAtLeast(0))
        editTriggerValue.setText(config?.trigger?.value.orEmpty())
        editAllowedSenders.setText(config?.allowedSenders?.joinToString("\n").orEmpty())
        editBlockedSenders.setText(config?.blockedSenders?.joinToString("\n").orEmpty())
        editCannedReplies.setText(config?.cannedReplies?.joinToString("\n").orEmpty())
        val roomTarget = AppSettings.getRoomTarget(this, roomName)
        if (roomTarget == null || roomTarget.lastImportedAt <= 0L) {
            val autoMemory = AutoMemoryStore.getSummary(this, roomName)
            roomMetaText.text = if (autoMemory.isBlank()) {
                "가져온 CSV 정보가 없습니다. 필요한 맥락을 직접 메모해둘 수 있습니다."
            } else {
                "자동 메모리가 최근 대화 기준으로 함께 관리됩니다."
            }
            return
        }

        val formatter = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        val importSource = roomTarget.lastImportSource ?: "CSV"
        roomMetaText.text = "최근 가져오기: ${importSource} · ${formatter.format(Date(roomTarget.lastImportedAt))}\n자동 메모리가 최근 대화 기준으로 함께 관리됩니다."
    }

    private fun configureSpinner(spinner: Spinner, items: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun splitLines(raw: String): List<String> {
        return raw.split('\n', ',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun mapTriggerMode(label: String): String {
        return when (label) {
            "호출어/멘션만" -> "mention"
            "질문/명령만" -> "question"
            "특정 키워드" -> "keyword"
            "모든 메시지" -> "always"
            else -> "ai_judge"
        }
    }

    private fun readableTriggerMode(mode: String?): String {
        return when (mode?.lowercase()) {
            "mention" -> "호출어/멘션만"
            "question" -> "질문/명령만"
            "keyword", "contains" -> "특정 키워드"
            "always" -> "모든 메시지"
            else -> "AI가 판단"
        }
    }
}
