package com.example.kakaotalkautobot

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var deviceNameText: TextView
    private lateinit var statusIndicator: View
    private lateinit var sessionSummaryText: TextView
    private lateinit var identitySummaryText: TextView
    private lateinit var providerSummaryText: TextView
    private lateinit var behaviorSummaryText: TextView
    private lateinit var roomSummaryText: TextView
    private lateinit var roomHistorySummaryText: TextView
    private lateinit var roomEmptyText: TextView
    private lateinit var logText: TextView
    private lateinit var copyLogsButton: MaterialButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var roomAdapter: RoomTargetAdapter
    private lateinit var replySwitch: SwitchMaterial
    private lateinit var permissionButton: MaterialButton
    private lateinit var editConfigButton: MaterialButton
    private lateinit var manageRoomsButton: MaterialButton

    private val roomTargets = mutableListOf<AppSettings.RoomTarget>()
    private val logLines = mutableListOf<String>()
    private val maxLogLines = 100
    private var receiverRegistered = false
    private var bindingReplySwitch = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.example.kakaotalkautobot.STATUS_UPDATE" -> refreshStatusUi()
                "com.example.kakaotalkautobot.LOG_UPDATE" -> appendLogLine(intent.getStringExtra("log") ?: return)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        deviceNameText = findViewById(R.id.device_name_text)
        statusIndicator = findViewById(R.id.status_indicator)
        sessionSummaryText = findViewById(R.id.text_session_summary)
        identitySummaryText = findViewById(R.id.text_identity_summary)
        providerSummaryText = findViewById(R.id.text_provider_summary)
        behaviorSummaryText = findViewById(R.id.text_behavior_summary)
        roomSummaryText = findViewById(R.id.text_room_summary)
        roomHistorySummaryText = findViewById(R.id.text_room_history_summary)
        roomEmptyText = findViewById(R.id.text_room_empty)
        logText = findViewById(R.id.log_text)
        copyLogsButton = findViewById(R.id.btn_copy_logs)
        recyclerView = findViewById(R.id.recycler_rooms)
        replySwitch = findViewById(R.id.switch_ai_replies)
        permissionButton = findViewById(R.id.permission_button)
        editConfigButton = findViewById(R.id.btn_edit_config)
        manageRoomsButton = findViewById(R.id.btn_manage_rooms)

        logText.movementMethod = ScrollingMovementMethod.getInstance()
        logText.setOnTouchListener { v, _ ->
            v.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }

        copyLogsButton.setOnClickListener { copyAllLogsToClipboard() }

        recyclerView.layoutManager = LinearLayoutManager(this)
        roomAdapter = RoomTargetAdapter(
            roomTargets,
            secondaryActionLabel = "메모",
            onRoomClick = { room -> openRoomMemory(room.name) },
            onSecondaryActionClick = { room -> openRoomMemory(room.name) },
            onDeleteClick = { room ->
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("대상 방 제거")
                    .setMessage("'${room.name}' 방을 대상 목록에서 제거할까요?")
                    .setPositiveButton("제거") { _, _ ->
                        BotManager.deleteBot(this, room.name)
                        AppSettings.removeRoomTarget(this, room.name)
                        loadRoomTargets()
                    }
                    .setNegativeButton("취소", null)
                    .show()
            },
            onToggleChanged = { room, isChecked ->
                BotManager.setBotEnabled(this, room.name, isChecked)
                loadRoomTargets()
            }
        )
        recyclerView.adapter = roomAdapter

        permissionButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        editConfigButton.setOnClickListener {
            startActivity(Intent(this, EditBotActivity::class.java))
        }

        manageRoomsButton.setOnClickListener {
            startActivity(Intent(this, CreatePollingBotActivity::class.java))
        }

        replySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (bindingReplySwitch) return@setOnCheckedChangeListener
            AppSettings.setAiReplyEnabled(this, isChecked)
            refreshStatusUi()
        }
    }

    override fun onResume() {
        super.onResume()

        loadRoomTargets()
        updateConfigSummary()
        updateDeviceNameUi()
        loadLogHistory()
        refreshStatusUi()
        requestRebindIfNeeded()

        val filter = IntentFilter().apply {
            addAction("com.example.kakaotalkautobot.STATUS_UPDATE")
            addAction("com.example.kakaotalkautobot.LOG_UPDATE")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        receiverRegistered = true
    }

    override fun onPause() {
        super.onPause()
        if (receiverRegistered) {
            unregisterReceiver(receiver)
            receiverRegistered = false
        }
    }

    private enum class StatusState {
        ACTIVE,
        CAPTURE_ONLY,
        DISCONNECTED
    }

    private fun refreshStatusUi() {
        val permissionGranted = NotificationManagerCompat.getEnabledListenerPackages(this)
            .contains(packageName)
        val aiReplyEnabled = AppSettings.isAiReplyEnabled(this)
        syncReplySwitch(aiReplyEnabled)

        if (!permissionGranted) {
            applyStatusUi("권한 필요 (알림 접근 허용)", StatusState.DISCONNECTED)
            updateRoomSummary()
            updateSessionSummary()
            return
        }

        if (!aiReplyEnabled) {
            applyStatusUi("AI 답장 OFF · 메시지 수집 중", StatusState.CAPTURE_ONLY)
            updateRoomSummary()
            updateSessionSummary()
            return
        }

        val stored = StatusStore.get(this)
        if (stored != null && stored.isConnected) {
            applyStatusUi("AI 답장 ON · 카카오톡 수신 대기 중", StatusState.ACTIVE)
        } else {
            applyStatusUi("권한 허용됨 · 연결 대기 중", StatusState.CAPTURE_ONLY)
        }
        updateRoomSummary()
        updateSessionSummary()
    }

    private fun requestRebindIfNeeded() {
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(this)
            .contains(packageName)
        if (!enabled) return
        NotificationListenerService.requestRebind(ComponentName(this, NotificationListener::class.java))
    }

    private fun updateDeviceNameUi() {
        val aiConfig = AppSettings.getAiConfig(this)
        val displayName = aiConfig.displayName.trim().ifBlank { "미설정" }
        deviceNameText.text = "내 이름: $displayName · Provider: ${aiConfig.provider}"
    }

    private fun applyStatusUi(status: String, state: StatusState) {
        statusText.text = status
        val color = when (state) {
            StatusState.ACTIVE -> ContextCompat.getColor(this, R.color.colorSuccess)
            StatusState.CAPTURE_ONLY -> ContextCompat.getColor(this, R.color.colorInfo)
            StatusState.DISCONNECTED -> ContextCompat.getColor(this, R.color.colorDanger)
        }
        statusIndicator.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
    }

    private fun loadLogHistory() {
        logLines.clear()
        val history = LogStore.getAll(this)
        if (history.isBlank()) {
            logText.text = "아직 수집된 로그가 없습니다."
            return
        }
        history.split("\n")
            .filter { it.isNotBlank() }
            .forEach { logLines.add(it) }
        trimLogLines()
        renderLogPreview()
    }

    private fun appendLogLine(line: String) {
        logLines.add(line)
        trimLogLines()
        renderLogPreview()
    }

    private fun trimLogLines() {
        if (logLines.size <= maxLogLines) return
        val overflow = logLines.size - maxLogLines
        if (overflow > 0) {
            logLines.subList(0, overflow).clear()
        }
    }

    private fun renderLogPreview() {
        if (logLines.isEmpty()) {
            logText.text = "아직 수집된 로그가 없습니다."
            return
        }
        logText.text = logLines.takeLast(12).joinToString("\n")
        scrollLogToBottom()
    }

    private fun scrollLogToBottom() {
        logText.post {
            val layout = logText.layout ?: return@post
            val scrollAmount = layout.getLineTop(logText.lineCount) - logText.height
            if (scrollAmount > 0) {
                logText.scrollTo(0, scrollAmount)
            } else {
                logText.scrollTo(0, 0)
            }
        }
    }

    private fun copyAllLogsToClipboard() {
        val allLogs = LogStore.getAll(this)
        if (allLogs.isBlank()) {
            Toast.makeText(this, "복사할 로그가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText("kakao-talk-auto-bot logs", allLogs)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "로그가 복사되었습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun updateConfigSummary() {
        val config = AppSettings.getAiConfig(this)
        identitySummaryText.text = "이름: ${config.displayName} · 페르소나: ${config.persona.take(40)}"
        providerSummaryText.text = "Provider: ${config.provider} · API 키: ${config.apiKeyMode}"
        behaviorSummaryText.text = "답장 방식: ${config.replyMode} · 트리거: ${config.triggerMode}"
    }

    private fun loadRoomTargets() {
        val rooms = BotManager.getBots(this)
            .filterNot { it.name == "기본 자동응답" }
            .map { bot ->
                val metadata = AppSettings.getRoomTarget(this, bot.roomPattern)
                AppSettings.RoomTarget(
                    name = bot.roomPattern,
                    isEnabled = bot.isEnabled,
                    lastImportedAt = metadata?.lastImportedAt ?: 0L,
                    lastImportSource = metadata?.lastImportSource
                )
            }
        roomAdapter.replaceItems(rooms)
        roomEmptyText.visibility = if (rooms.isEmpty()) View.VISIBLE else View.GONE
        updateRoomSummary()
        updateSessionSummary()
    }

    private fun updateRoomSummary() {
        val allRooms = BotManager.getBots(this).filterNot { it.name == "기본 자동응답" }
        val enabledCount = allRooms.count { it.isEnabled }
        roomSummaryText.text = "응답 대상 ${enabledCount}개 · 전체 저장 ${allRooms.size}개"

        val recentImport = AppSettings.getMostRecentImportedRoom(this)
        roomHistorySummaryText.text = if (recentImport == null) {
            "가져온 CSV가 아직 없습니다."
        } else {
            val formatter = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
            val importedAt = formatter.format(Date(recentImport.lastImportedAt))
            val source = recentImport.lastImportSource ?: "CSV"
            "최근 가져오기: ${recentImport.name} · ${source} · ${importedAt}"
        }
    }

    private fun updateSessionSummary() {
        val knownRooms = SessionManager.getRegisteredRooms(this)
        val configuredRooms = BotManager.getBots(this).count { it.name != "기본 자동응답" }
        sessionSummaryText.text = "대상 방 ${configuredRooms}개 · 최근 감지 방 ${knownRooms.size}개"
    }

    private fun syncReplySwitch(enabled: Boolean) {
        bindingReplySwitch = true
        replySwitch.isChecked = enabled
        bindingReplySwitch = false
    }

    private fun openRoomMemory(roomName: String) {
        startActivity(
            Intent(this, DebugRoomActivity::class.java)
                .putExtra("roomName", roomName)
        )
    }
}
