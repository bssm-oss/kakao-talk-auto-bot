package com.example.chatbotchichi

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class EditBotActivity : AppCompatActivity() {
    private lateinit var editName: EditText
    private lateinit var editCode: EditText
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_bot)

        editName = findViewById(R.id.edit_bot_name)
        editCode = findViewById(R.id.edit_code)
        btnSave = findViewById(R.id.btn_save)

        val botName = intent.getStringExtra("botName")
        if (botName != null) {
            editName.setText(botName)
            editName.isEnabled = false // 이름 수정 불가 (파일명이므로)
            editCode.setText(BotManager.getBotCode(this, botName))
        } else {
            // 새 봇 생성 시 기본 템플릿
            editCode.setText(
                """
                function responseFix(room, msg, sender, isGroupChat, replier, imageDB, packageName) {
                    if (msg == "/test") {
                        replier.reply("작동 확인!");
                    }
                }
                """.trimIndent()
            )
        }

        btnSave.setOnClickListener {
            val name = editName.text.toString().trim()
            val code = editCode.text.toString()

            if (name.isEmpty()) {
                Toast.makeText(this, "이름을 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            BotManager.saveBot(this, name, code)
            Toast.makeText(this, "저장되었습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
