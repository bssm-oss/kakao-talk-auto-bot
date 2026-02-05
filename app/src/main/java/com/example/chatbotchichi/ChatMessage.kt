package com.example.chatbotchichi

data class ChatMessage(
    val name: String,
    val message: String,
    val time: String,
    val isMe: Boolean // true=오른쪽(나/테스터), false=왼쪽(봇)
)
