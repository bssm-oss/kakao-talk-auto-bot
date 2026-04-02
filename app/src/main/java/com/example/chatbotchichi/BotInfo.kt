package com.example.kakaotalkautobot

data class BotInfo(
    val name: String,
    val fileName: String,
    var isEnabled: Boolean = false,
    val roomPattern: String = name,
    val captureEnabled: Boolean = true,
    val replyEnabled: Boolean = true,
    val replyMode: String = "provider"
)
