package com.example.kakaotalkautobot

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.io.BufferedReader
import java.io.InputStreamReader

class CreatePollingBotActivity : AppCompatActivity() {
    private lateinit var rootScroll: NestedScrollView
    private lateinit var inputRoomName: TextInputEditText
    private lateinit var addRoomButton: MaterialButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView

    private val roomTargets = mutableListOf<AppSettings.RoomTarget>()
    private lateinit var roomAdapter: RoomTargetAdapter
    private var pendingImportRoom: String? = null

    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        handleImportedUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_polling)

        rootScroll = findViewById(R.id.create_polling_scroll)
        inputRoomName = findViewById(R.id.input_room_name)
        addRoomButton = findViewById(R.id.btn_add_room)
        recyclerView = findViewById(R.id.recycler_rooms)
        emptyText = findViewById(R.id.text_room_empty)

        recyclerView.layoutManager = LinearLayoutManager(this)
        rootScroll.bindFocusScroll(inputRoomName)
        roomAdapter = RoomTargetAdapter(
            roomTargets,
            secondaryActionLabel = "CSV",
            onRoomClick = { room -> openMemoryEditor(room.name) },
            onSecondaryActionClick = { room ->
                pendingImportRoom = room.name
                openDocumentLauncher.launch(arrayOf("*/*"))
            },
            onDeleteClick = { room ->
                BotManager.deleteBot(this, room.name)
                AppSettings.removeRoomTarget(this, room.name)
                loadRoomTargets()
            },
            onToggleChanged = { room, isChecked ->
                BotManager.setBotEnabled(this, room.name, isChecked)
                loadRoomTargets()
            }
        )
        recyclerView.adapter = roomAdapter

        addRoomButton.setOnClickListener {
            val roomName = inputRoomName.text?.toString()?.trim().orEmpty()
            if (roomName.isEmpty()) {
                Toast.makeText(this, "방 이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val exists = BotManager.getConfigByRoomPattern(this, roomName) != null
            if (exists) {
                Toast.makeText(this, "이미 등록된 방이거나 이름이 비어 있습니다.", Toast.LENGTH_SHORT).show()
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

    private fun handleImportedUri(uri: Uri?) {
        val roomName = pendingImportRoom
        pendingImportRoom = null

        if (roomName.isNullOrBlank()) return
        if (uri == null) {
            Toast.makeText(this, "CSV 가져오기를 취소했습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val importedText = readUriText(uri)
        if (importedText.isBlank()) {
            Toast.makeText(this, "가져올 내용이 없거나 읽을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        RoomStore.importHistory(this, roomName, importedText)
        AppSettings.markRoomImported(this, roomName, resolveSourceLabel(uri))
        val importedLines = importedText.lineSequence().count { it.isNotBlank() }

        if (importedLines <= 0) {
            Toast.makeText(this, "CSV를 처리하지 못했습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        loadRoomTargets()
        Toast.makeText(this, "${roomName} 방에 CSV ${importedLines}줄을 가져왔습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun readUriText(uri: Uri): String {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).readText()
            }.orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    private fun resolveSourceLabel(uri: Uri): String {
        return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "history.csv"
    }
}
