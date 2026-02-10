package com.example.chatbotchichi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DebugRoomActivity : AppCompatActivity() {
    private lateinit var recyclerChat: RecyclerView
    private lateinit var editRoom: EditText
    private lateinit var editSender: EditText
    private lateinit var editMsg: EditText
    private lateinit var btnSend: Button
    private lateinit var btnBack: ImageButton
    
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter
    private val timeFormat = SimpleDateFormat("a h:mm", Locale.getDefault())
    private val maxMessages = 300
    private var receiverRegistered = false

    private val replyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.chatbotchichi.BOT_REPLY") {
                val msg = intent.getStringExtra("msg") ?: return
                // 봇의 답장을 채팅창에 추가 (왼쪽)
                addMessage("봇", msg, false)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_room)

        recyclerChat = findViewById(R.id.recycler_chat)
        editRoom = findViewById(R.id.edit_room_name)
        editSender = findViewById(R.id.edit_sender_name)
        editMsg = findViewById(R.id.edit_message)
        btnSend = findViewById(R.id.btn_send)
        btnBack = findViewById(R.id.btn_back)

        adapter = ChatAdapter(messages)
        recyclerChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true // 키보드 올라올 때 편리함
        }
        recyclerChat.setHasFixedSize(true)
        recyclerChat.itemAnimator = null
        recyclerChat.adapter = adapter

        btnBack.setOnClickListener {
            finish()
        }

        btnSend.setOnClickListener {
            val msg = editMsg.text.toString()
            if (msg.isNotEmpty()) {
                val room = editRoom.text.toString().ifBlank { "테스트" }
                val sender = editSender.text.toString().ifBlank { "테스터" }
                
                // 내 메시지 추가 (오른쪽)
                addMessage(sender, msg, true)

                // 봇에게 가상 메시지 전송
                val intent = Intent("com.example.chatbotchichi.DEBUG_MSG")
                intent.putExtra("room", room)
                intent.putExtra("msg", msg)
                intent.putExtra("sender", sender)
                intent.putExtra("packageName", "com.kakao.talk") // 카톡인 척
                intent.setPackage(packageName) // 내 앱으로 전송
                sendBroadcast(intent)

                editMsg.text.clear()
            }
        }
    }

    private fun addMessage(name: String, msg: String, isMe: Boolean) {
        val time = timeFormat.format(Date())
        messages.add(ChatMessage(name, msg, time, isMe))
        if (messages.size > maxMessages) {
            val overflow = messages.size - maxMessages
            messages.subList(0, overflow).clear()
            adapter.notifyDataSetChanged()
        } else {
            adapter.notifyItemInserted(messages.size - 1)
        }
        recyclerChat.scrollToPosition(messages.size - 1)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("com.example.chatbotchichi.BOT_REPLY")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(replyReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(replyReceiver, filter)
        }
        receiverRegistered = true
    }

    override fun onPause() {
        super.onPause()
        if (receiverRegistered) {
            try {
                unregisterReceiver(replyReceiver)
            } catch (e: Exception) {
                // ignore
            } finally {
                receiverRegistered = false
            }
        }
    }
}
