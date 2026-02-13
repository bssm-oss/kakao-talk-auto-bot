package com.example.chatbotchichi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.text.method.ScrollingMovementMethod
import android.text.Editable
import android.text.TextWatcher
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import android.service.notification.NotificationListenerService
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var statusIndicator: android.view.View
    private lateinit var logText: TextView
    private lateinit var logFilterInput: TextInputEditText
    private lateinit var logErrorSwitch: SwitchMaterial
    private lateinit var copyLogsButton: MaterialButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: BotAdapter
    private lateinit var globalSwitch: Switch
    
    private var bots = mutableListOf<BotInfo>()
    private val logBuilder = StringBuilder()
    private val logLines = mutableListOf<String>()
    private var logFilterText: String = ""
    private var logErrorOnly: Boolean = false
    private val maxLogLines = 100
    private var deviceDialogShown = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.example.chatbotchichi.STATUS_UPDATE" -> {
                    val status = intent.getStringExtra("status")
                    val connected = intent.getBooleanExtra("connected", false)
                    val globalEnabled = AppSettings.isGlobalEnabled(this@MainActivity)
                    if (!globalEnabled) {
                        applyStatusUi("전체 OFF (수집 중지)", StatusState.DISCONNECTED)
                        return
                    }
                    if (status != null) {
                        val state = if (connected) StatusState.CONNECTED else StatusState.DISCONNECTED
                        applyStatusUi(status, state)
                    }
                }
                "com.example.chatbotchichi.LOG_UPDATE" -> {
                    val msg = intent.getStringExtra("log") ?: return
                    appendLogLine(msg)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        statusIndicator = findViewById(R.id.status_indicator)
        logText = findViewById(R.id.log_text)
        logFilterInput = findViewById(R.id.input_log_filter)
        logErrorSwitch = findViewById(R.id.switch_log_error)
        copyLogsButton = findViewById(R.id.btn_copy_logs)
        recyclerView = findViewById(R.id.recycler_view)
        globalSwitch = findViewById(R.id.switch_global)

        logText.movementMethod = ScrollingMovementMethod.getInstance()
        logText.setOnTouchListener { v, _ ->
            v.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }
        logFilterInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                logFilterText = s?.toString() ?: ""
                applyLogFilter()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        logErrorSwitch.setOnCheckedChangeListener { _, isChecked ->
            logErrorOnly = isChecked
            applyLogFilter()
        }
        copyLogsButton.setOnClickListener {
            copyAllLogsToClipboard()
        }
        
        val btnPermission = findViewById<Button>(R.id.permission_button)
        val btnShowSessions = findViewById<Button>(R.id.btn_show_sessions)
        val btnAddBot = findViewById<Button>(R.id.btn_add_bot)
        val btnAddPolling = findViewById<Button>(R.id.btn_add_polling)
        val btnOpenDebug = findViewById<Button>(R.id.btn_open_debug)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = BotAdapter(
            bots,
            { bot ->
                val intent = Intent(this, EditBotActivity::class.java)
                intent.putExtra("botName", bot.name)
                startActivity(intent)
            },
            { bot ->
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("봇 삭제")
                    .setMessage("'${bot.name}' 봇을 삭제할까요?")
                    .setPositiveButton("삭제") { _, _ ->
                        deleteBot(bot)
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }
        )
        recyclerView.adapter = adapter

        btnPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        btnShowSessions.setOnClickListener {
            val rooms = SessionManager.getRegisteredRooms(this)
            val message = if (rooms.isEmpty()) {
                "활성화된 세션(방)이 없습니다.\n알림이 오면 자동으로 추가됩니다."
            } else {
                rooms.joinToString("\n") { formatRoomEntry(it) }
            }
            
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("활성 세션 목록 (${rooms.size}개)")
                .setMessage(message)
                .setPositiveButton("확인", null)
                .show()
        }

        btnAddBot.setOnClickListener {
            startActivity(Intent(this, EditBotActivity::class.java))
        }

        btnAddPolling.setOnClickListener {
            startActivity(Intent(this, CreatePollingBotActivity::class.java))
        }

        btnOpenDebug.setOnClickListener {
            startActivity(Intent(this, DebugRoomActivity::class.java))
        }

        globalSwitch.isChecked = AppSettings.isGlobalEnabled(this)
        globalSwitch.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setGlobalEnabled(this, isChecked)
            sendGlobalCommand(isChecked)
            refreshStatusUi()
        }
    }

    override fun onResume() {
        super.onResume()
        
        // 봇 목록 갱신
        bots.clear()
        bots.addAll(BotManager.getBots(this))
        adapter.notifyDataSetChanged()

        loadLogHistory()
        refreshStatusUi()
        requestRebindIfNeeded()
        ensureDeviceConfigured()

        val filter = IntentFilter().apply {
            addAction("com.example.chatbotchichi.STATUS_UPDATE")
            addAction("com.example.chatbotchichi.LOG_UPDATE")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    private fun ensureDeviceConfigured() {
        if (deviceDialogShown) return
        val current = DeviceSettings.getDeviceName(this)
        if (!current.isNullOrBlank()) return
        deviceDialogShown = true
        ApiClient.fetchDevices { devices, error ->
            runOnUiThread {
                val names = devices?.map { it.name } ?: emptyList()
                showDeviceDialog(names, error)
            }
        }
    }

    private fun showDeviceDialog(existingNames: List<String>, error: String?) {
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val listLabel = android.widget.TextView(this).apply {
            text = if (error != null) {
                "디바이스 목록을 불러오지 못했습니다. 새 이름을 입력해주세요."
            } else if (existingNames.isEmpty()) {
                "등록된 디바이스가 없습니다. 새 이름을 입력해주세요."
            } else {
                "기존 디바이스 목록:\n" + existingNames.joinToString(", ")
            }
        }
        val hintLabel = android.widget.TextView(this).apply {
            text = "이 중에 없는 디바이스 이름을 입력해주세요."
            setPadding(0, 16, 0, 8)
        }
        val input = android.widget.EditText(this).apply {
            hint = "디바이스 이름"
        }
        container.addView(listLabel)
        container.addView(hintLabel)
        container.addView(input)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("디바이스 등록")
            .setView(container)
            .setCancelable(false)
            .setPositiveButton("등록", null)
            .create()

        dialog.setOnShowListener {
            val btn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            btn.setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    input.error = "이름을 입력해주세요."
                    return@setOnClickListener
                }
                if (existingNames.contains(name)) {
                    input.error = "이미 존재하는 이름입니다."
                    return@setOnClickListener
                }
                btn.isEnabled = false
                ApiClient.registerDevice(name) { device, err ->
                    runOnUiThread {
                        btn.isEnabled = true
                        if (device != null) {
                            DeviceSettings.saveDevice(this, device.name, device.id.takeIf { it > 0L })
                            Toast.makeText(this, "디바이스 등록 완료: ${device.name}", Toast.LENGTH_SHORT).show()
                            requestRebindIfNeeded()
                            if (AppSettings.isGlobalEnabled(this)) {
                                InboundPollingController.startIfPossible(applicationContext)
                            }
                            dialog.dismiss()
                        } else {
                            input.error = "등록 실패: ${err ?: "unknown"}"
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    private enum class StatusState {
        CONNECTED,
        PERMISSION_GRANTED,
        DISCONNECTED
    }

    private fun refreshStatusUi() {
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(this)
            .contains(packageName)
        val globalEnabled = AppSettings.isGlobalEnabled(this)
        if (!globalEnabled) {
            applyStatusUi("전체 OFF (수집 중지)", StatusState.DISCONNECTED)
            return
        }
        if (!enabled) {
            applyStatusUi("권한 필요 (알림 접근 허용)", StatusState.DISCONNECTED)
            return
        }

        val stored = StatusStore.get(this)
        if (stored != null && stored.isConnected) {
            applyStatusUi(stored.text, StatusState.CONNECTED)
        } else {
            applyStatusUi("권한 허용됨 (대기 중)", StatusState.PERMISSION_GRANTED)
        }
    }

    private fun sendGlobalCommand(start: Boolean) {
        val intent = Intent("com.example.chatbotchichi.DEBUG_MSG")
        intent.putExtra("room", "__SYSTEM__")
        intent.putExtra("msg", if (start) "__GLOBAL_START__" else "__GLOBAL_STOP__")
        intent.putExtra("sender", "SYSTEM")
        intent.putExtra("packageName", packageName)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    private fun requestRebindIfNeeded() {
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(this)
            .contains(packageName)
        if (!AppSettings.isGlobalEnabled(this)) return
        if (!enabled) return
        NotificationListenerService.requestRebind(ComponentName(this, NotificationListener::class.java))
    }

    private fun applyStatusUi(status: String, state: StatusState) {
        statusText.text = status
        val color = when (state) {
            StatusState.CONNECTED -> "#4CAF50"
            StatusState.PERMISSION_GRANTED -> "#FFB300"
            StatusState.DISCONNECTED -> "#FF5252"
        }
        statusIndicator.backgroundTintList =
            android.content.res.ColorStateList.valueOf(Color.parseColor(color))
    }

    private fun loadLogHistory() {
        val history = LogStore.getAll(this)
        logBuilder.clear()
        logLines.clear()
        if (history.isBlank()) {
            logText.text = "시스템 대기 중..."
            return
        }
        history.split("\n")
            .filter { it.isNotBlank() }
            .forEach { logLines.add(it) }
        trimLogLines()
        applyLogFilter()
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

    private fun appendLogLine(line: String) {
        logLines.add(line)
        trimLogLines()
        applyLogFilter()
    }

    private fun trimLogLines() {
        if (logLines.size <= maxLogLines) return
        val overflow = logLines.size - maxLogLines
        if (overflow > 0) {
            logLines.subList(0, overflow).clear()
        }
    }

    private fun applyLogFilter() {
        if (logLines.isEmpty()) {
            logText.text = "시스템 대기 중..."
            return
        }
        val query = logFilterText.trim()
        val filtered = logLines.filter { line ->
            val isError = line.contains("❌") || line.contains("ERROR", true) || line.contains("Error", true)
            val passError = !logErrorOnly || isError
            val passQuery = query.isBlank() || line.contains(query, true)
            passError && passQuery
        }
        if (filtered.isEmpty()) {
            logText.text = "필터 조건에 맞는 로그가 없습니다."
            return
        }
        logBuilder.clear()
        for (line in filtered) {
            logBuilder.append(line).append('\n')
        }
        logText.text = logBuilder.toString()
        scrollLogToBottom()
    }

    private fun copyAllLogsToClipboard() {
        val allLogs = LogStore.getAll(this)
        if (allLogs.isBlank()) {
            Toast.makeText(this, "복사할 로그가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText("Chatbotchichi Logs", allLogs)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "로그가 복사되었습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun formatRoomEntry(entry: SessionManager.RoomEntry): String {
        val timeLabel = if (entry.lastSeen > 0L) {
            val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            formatter.format(java.util.Date(entry.lastSeen))
        } else {
            null
        }
        val stateLabel = if (entry.isActive) "활성" else "최근"
        return if (timeLabel != null) {
            "• ${entry.name} ($stateLabel: $timeLabel)"
        } else {
            "• ${entry.name} ($stateLabel)"
        }
    }

    private fun deleteBot(bot: BotInfo) {
        val index = bots.indexOfFirst { it.name == bot.name }
        if (index == -1) return
        BotManager.deleteBot(this, bot.name)
        bots.removeAt(index)
        adapter.notifyItemRemoved(index)
    }
}
