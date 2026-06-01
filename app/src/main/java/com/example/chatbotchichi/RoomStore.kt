package com.example.kakaotalkautobot

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class RoomHistoryMessage(
    val sender: String,
    val message: String,
    val incoming: Boolean,
    val timestamp: Long
)

object RoomStore {
    private const val TAG = "RoomStore"
    private const val DIR_NAME = "room_state"
    private const val MAX_MESSAGES = 80

    @Synchronized
    fun recordIncoming(context: Context, room: String, sender: String, message: String) {
        updateRoom(context, room) { json ->
            appendMessage(json, RoomHistoryMessage(sender, message, true, System.currentTimeMillis()))
        }
    }

    @Synchronized
    fun recordOutgoing(context: Context, room: String, message: String, sender: String = "AI") {
        updateRoom(context, room) { json ->
            appendMessage(json, RoomHistoryMessage(sender, message, false, System.currentTimeMillis()))
        }
    }

    @Synchronized
    fun importHistory(context: Context, room: String, raw: String) {
        val parsed = parseImportedLines(raw)
        if (parsed.isEmpty()) return
        updateRoom(context, room) { json ->
            parsed.forEach { appendMessage(json, it) }
        }
    }

    @Synchronized
    fun recentMessages(context: Context, room: String, limit: Int = 20): List<RoomHistoryMessage> {
        val file = roomFile(context, room)
        if (!file.exists()) return emptyList()
        val json = readRoomJson(file, room, recoverCorruptFile = true) ?: return emptyList()
        return json.toHistoryMessages(limit)
    }

    private fun updateRoom(context: Context, room: String, block: (JSONObject) -> Unit) {
        val file = roomFile(context, room)
        val json = readRoomJson(file, room, recoverCorruptFile = true) ?: JSONObject().put("room", room)
        block(json)
        file.writeText(json.toString())
        AutoMemoryStore.refresh(context, room, json.toHistoryMessages())
    }

    private fun appendMessage(json: JSONObject, message: RoomHistoryMessage) {
        val messages = json.optJSONArray("messages") ?: JSONArray()
        messages.put(
            JSONObject()
                .put("sender", message.sender)
                .put("message", message.message)
                .put("incoming", message.incoming)
                .put("timestamp", message.timestamp)
        )
        while (messages.length() > MAX_MESSAGES) {
            messages.remove(0)
        }
        json.put("messages", messages)
        json.put("updatedAt", System.currentTimeMillis())
    }

    internal fun parseImportedLines(raw: String, timestamp: Long = System.currentTimeMillis()): List<RoomHistoryMessage> {
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val pipeParts = line.split("|").map { it.trim() }
                if (pipeParts.size >= 3) {
                    val sender = pipeParts[pipeParts.size - 2]
                    val message = pipeParts.last()
                    return@mapNotNull RoomHistoryMessage(sender, message, true, timestamp)
                }
                val colonIndex = line.indexOf(":")
                if (colonIndex in 1..50) {
                    val sender = line.substring(0, colonIndex).trim()
                    val message = line.substring(colonIndex + 1).trim()
                    if (sender.isNotBlank() && message.isNotBlank()) {
                        return@mapNotNull RoomHistoryMessage(sender, message, true, timestamp)
                    }
                }
                null
            }
            .toList()
    }

    internal fun parseHistoryJsonText(raw: String, limit: Int = 20): List<RoomHistoryMessage> {
        return runCatching { JSONObject(raw).toHistoryMessages(limit) }
            .getOrDefault(emptyList())
    }

    private fun roomFile(context: Context, room: String): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        val key = room.lowercase().replace(Regex("[^a-z0-9._-]+"), "_").trim('_')
        val safeName = (if (key.isBlank()) "room" else key.take(40)) + "_" + room.hashCode().toUInt().toString(16)
        return File(dir, "$safeName.json")
    }

    private fun readRoomJson(file: File, room: String, recoverCorruptFile: Boolean): JSONObject? {
        if (!file.exists()) return JSONObject().put("room", room)
        return runCatching { JSONObject(file.readText()) }
            .onFailure { error ->
                Log.w(TAG, "Room state is corrupted, resetting ${file.name}", error)
                if (recoverCorruptFile) {
                    quarantineCorruptFile(file)
                }
            }
            .getOrNull()
    }

    private fun quarantineCorruptFile(file: File) {
        val quarantine = File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}")
        if (!file.renameTo(quarantine)) {
            file.delete()
        }
    }

    private fun JSONObject.toHistoryMessages(limit: Int = MAX_MESSAGES): List<RoomHistoryMessage> {
        val messages = optJSONArray("messages") ?: return emptyList()
        val start = maxOf(0, messages.length() - limit)
        return buildList {
            for (index in start until messages.length()) {
                val item = messages.optJSONObject(index) ?: continue
                add(
                    RoomHistoryMessage(
                        sender = item.optString("sender", "알수없음"),
                        message = item.optString("message", ""),
                        incoming = item.optBoolean("incoming", true),
                        timestamp = item.optLong("timestamp", 0L)
                    )
                )
            }
        }
    }
}
