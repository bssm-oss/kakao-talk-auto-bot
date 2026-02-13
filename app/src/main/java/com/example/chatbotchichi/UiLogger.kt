package com.example.chatbotchichi

import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UiLogger {
    private val allowedLabels = setOf("IN", "OUT", "OUT_FAIL")

    fun log(
        context: Context,
        label: String,
        message: String,
        roomName: String? = null,
        speaker: String? = null,
        serverMessage: String? = null
    ) {
        if (!allowedLabels.contains(label)) return
        val normalizedMessage = if (label == "OUT_FAIL" && !message.trimStart().startsWith("❌")) {
            "❌ $message"
        } else {
            message
        }
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val line = "[$time][$label] $normalizedMessage"
        LogStore.append(context, line)
        val intent = Intent("com.example.chatbotchichi.LOG_UPDATE")
        intent.putExtra("log", line)
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)
        sendLogToServer(
            context = context,
            label = label,
            normalizedMessage = normalizedMessage,
            roomName = roomName,
            speaker = speaker,
            serverMessage = serverMessage
        )
    }

    private fun sendLogToServer(
        context: Context,
        label: String,
        normalizedMessage: String,
        roomName: String?,
        speaker: String?,
        serverMessage: String?
    ) {
        val status = when (label) {
            "IN" -> "in"
            "OUT" -> "out"
            "OUT_FAIL" -> "fail"
            else -> "fail"
        }
        val (roomNameRaw, speakerRaw, cleanMessage) = if (
            roomName != null || speaker != null || serverMessage != null
        ) {
            Triple(roomName, speaker, serverMessage ?: normalizedMessage)
        } else {
            extractRoomSpeakerMessage(normalizedMessage)
        }
        val resolvedRoomName = if (roomNameRaw.isNullOrBlank()) "없음" else roomNameRaw
        val resolvedSpeaker = if (speakerRaw.isNullOrBlank()) "시스템" else speakerRaw
        val deviceName = DeviceSettings.getDeviceName(context)?.ifBlank { "unknown" } ?: "unknown"
        ApiClient.postLog(status, cleanMessage, resolvedRoomName, resolvedSpeaker, deviceName)
    }

    private fun extractRoomSpeakerMessage(raw: String): Triple<String?, String?, String> {
        var text = raw.trim()
        if (text.startsWith("❌")) {
            text = text.removePrefix("❌").trim()
        }
        val prefixRegex = Regex("""^\[\d{2}:\d{2}:\d{2}]\[(IN|OUT|OUT_FAIL)]\s*""")
        text = text.replace(prefixRegex, "")
        if (text.startsWith("[")) {
            val end = text.lastIndexOf("] ")
            val endBracket = if (end > 1) end else text.lastIndexOf(']')
            if (endBracket > 1) {
                val room = text.substring(1, endBracket).trim()
                var rest = text.substring(endBracket + 1).trim()
                if (rest.startsWith(":")) {
                    rest = rest.removePrefix(":").trim()
                }
                val speakerSplit = rest.indexOf(": ")
                return if (speakerSplit in 1..60) {
                    val speaker = rest.substring(0, speakerSplit).trim()
                    val msg = rest.substring(speakerSplit + 2).trim()
                    Triple(room, speaker, msg)
                } else {
                    Triple(room, null, rest)
                }
            }
        }
        return Triple(null, null, text)
    }
}
