package com.example.kakaotalkautobot

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

class CreatePollingBotActivity : AppCompatActivity() {
    private lateinit var rootScroll: NestedScrollView
    private lateinit var inputRoomName: TextInputEditText
    private lateinit var addRoomButton: MaterialButton
    private lateinit var switchAllRooms: SwitchMaterial
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView

    private val roomTargets = mutableListOf<AppSettings.RoomTarget>()
    private lateinit var roomAdapter: RoomTargetAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_polling)

        rootScroll = findViewById(R.id.create_polling_scroll)
        inputRoomName = findViewById(R.id.input_room_name)
        addRoomButton = findViewById(R.id.btn_add_room)
        switchAllRooms = findViewById(R.id.switch_all_rooms)
        recyclerView = findViewById(R.id.recycler_rooms)
        emptyText = findViewById(R.id.text_room_empty)

        recyclerView.layoutManager = LinearLayoutManager(this)
        rootScroll.bindFocusScroll(inputRoomName)
        roomAdapter = RoomTargetAdapter(
            roomTargets,
            secondaryActionLabel = "메모",
            onRoomClick = { room -> openMemoryEditor(room.name) },
            onSecondaryActionClick = { room -> openMemoryEditor(room.name) },
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

        // Load "all rooms" setting
        switchAllRooms.isChecked = isAllRoomsEnabled()
        switchAllRooms.setOnCheckedChangeListener { _, isChecked ->
            setAllRoomsEnabled(isChecked)
            if (isChecked) {
                Toast.makeText(this, "모든 채팅방에서 AI 답장이 활성화됩니다.", Toast.LENGTH_LONG).show()
            }
        }

        addRoomButton.setOnClickListener {
            val roomName = inputRoomName.text?.toString()?.trim().orEmpty()
            if (roomName.isEmpty()) {
                Toast.makeText(this, "방 이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val exists = BotManager.getConfigByRoomPattern(this, roomName) != null
            if (exists) {
                Toast.makeText(this, "이미 등록된 방입니다.", Toast.LENGTH_SHORT).show()
            } else {
                val baseConfig = BotManager.getConfig(this, "기본 자동응답")
                    ?: AutoReplyJson.defaultConfig("기본 자동응답")
                val roomConfig = baseConfig.copy(
                    name = roomName,
                    roomPattern = roomName,
                    enabled = true,
                    captureEnabled = true,
                    replyEnabled = true,
                    roomMemory = "",
                    importHistory = ""
                )
                BotManager.saveConfig(this, roomConfig)
                inputRoomName.text?.clear()
                Toast.makeText(this, "대상 방을 추가했습니다.", Toast.LENGTH_SHORT).show()
                loadRoomTargets()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadRoomTargets()
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
        emptyText.visibility = if (rooms.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openMemoryEditor(roomName: String) {
        startActivity(
            android.content.Intent(this, DebugRoomActivity::class.java)
                .putExtra("roomName", roomName)
        )
    }

    private fun isAllRoomsEnabled(): Boolean {
        val prefs = getSharedPreferences("AppSettingsPrefs", MODE_PRIVATE)
        return prefs.getBoolean("all_rooms_enabled", false)
    }

    private fun setAllRoomsEnabled(enabled: Boolean) {
        val prefs = getSharedPreferences("AppSettingsPrefs", MODE_PRIVATE)
        prefs.edit().putBoolean("all_rooms_enabled", enabled).apply()
    }
}
