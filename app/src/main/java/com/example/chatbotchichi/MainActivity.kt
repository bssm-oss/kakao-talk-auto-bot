package com.example.chatbotchichi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: BotAdapter
    
    private var bots = mutableListOf<BotInfo>()

    // 로그 텍스트 누적용
    private val logBuilder = StringBuilder()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.example.chatbotchichi.STATUS_UPDATE" -> {
                    val status = intent.getStringExtra("status")
                    statusText.text = "엔진 상태: $status"
                    if (status?.contains("연결됨") == true) {
                        statusText.setTextColor(Color.BLUE)
                    } else {
                        statusText.setTextColor(Color.RED)
                    }
                }
                "com.example.chatbotchichi.LOG_UPDATE" -> {
                    val msg = intent.getStringExtra("log") ?: return
                    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    logBuilder.append("[$time] $msg\n")
                    if (logBuilder.length > 5000) logBuilder.delete(0, 1000)
                    logText.text = logBuilder.toString()
                    val scrollView = logText.parent as? android.widget.ScrollView
                    scrollView?.post { scrollView.fullScroll(android.view.View.FOCUS_DOWN) }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        logText = findViewById(R.id.log_text)
        recyclerView = findViewById(R.id.recycler_view)
        
        val btnPermission = findViewById<Button>(R.id.permission_button)
        val btnAddBot = findViewById<Button>(R.id.btn_add_bot)
        val btnOpenDebug = findViewById<Button>(R.id.btn_open_debug)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = BotAdapter(bots) { bot ->
            // 봇 클릭 시 편집 화면으로
            val intent = Intent(this, EditBotActivity::class.java)
            intent.putExtra("botName", bot.name)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        btnPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        btnAddBot.setOnClickListener {
            // 새 봇 추가 (편집 화면 열기)
            startActivity(Intent(this, EditBotActivity::class.java))
        }

        btnOpenDebug.setOnClickListener {
            // 디버깅 룸 열기
            startActivity(Intent(this, DebugRoomActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        
        // 봇 목록 갱신
        bots.clear()
        bots.addAll(BotManager.getBots(this))
        adapter.notifyDataSetChanged()

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

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }
}
