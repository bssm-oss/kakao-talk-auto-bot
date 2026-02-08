package com.example.chatbotchichi

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class CreatePollingBotActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_polling)

        val inputName = findViewById<TextInputEditText>(R.id.input_bot_name)
        val inputUrl = findViewById<TextInputEditText>(R.id.input_url)
        val inputRoom = findViewById<TextInputEditText>(R.id.input_room)
        val inputInterval = findViewById<TextInputEditText>(R.id.input_interval)
        val btnCreate = findViewById<Button>(R.id.btn_create)

        btnCreate.setOnClickListener {
            val name = inputName.text.toString().trim()
            val url = inputUrl.text.toString().trim()
            val room = inputRoom.text.toString().trim()
            val intervalSec = inputInterval.text.toString().trim().toLongOrNull() ?: 1

            if (name.isEmpty() || url.isEmpty()) {
                Toast.makeText(this, "이름과 URL을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val jsCode = PollingBotTemplate.generate(
                botId = name,
                url = url,
                room = room,
                intervalMs = intervalSec * 1000
            )

            BotManager.saveBot(this, name, jsCode)
            Toast.makeText(this, "봇이 생성되었습니다: $name", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
