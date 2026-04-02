package com.example.kakaotalkautobot

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import org.json.JSONArray
import org.json.JSONObject

object AppSettings {
    private const val PREFS_NAME = "AppSettingsPrefs"
    private const val KEY_AI_REPLY_ENABLED = "ai_reply_enabled"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_PERSONA = "persona"
    private const val KEY_PROVIDER = "provider"
    private const val KEY_API_KEY_MODE = "api_key_mode"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_REPLY_MODE = "reply_mode"
    private const val KEY_TRIGGER_MODE = "trigger_mode"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_ROOM_TARGETS = "room_targets"
    private const val KEY_ROOM_MEMORIES = "room_memories"

    data class AiConfig(
        val displayName: String,
        val persona: String,
        val provider: String,
        val apiKeyMode: String,
        val apiKey: String,
        val replyMode: String,
        val triggerMode: String
    )

    data class RoomTarget(
        val name: String,
        val isEnabled: Boolean,
        val lastImportedAt: Long,
        val lastImportSource: String?
    )

    enum class ThemeMode(val preferenceValue: String, val appCompatMode: Int) {
        SYSTEM("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        LIGHT("light", AppCompatDelegate.MODE_NIGHT_NO),
        DARK("dark", AppCompatDelegate.MODE_NIGHT_YES);

        companion object {
            fun fromPreference(value: String?): ThemeMode {
                return entries.firstOrNull { it.preferenceValue == value } ?: SYSTEM
            }
        }
    }

    fun isAiReplyEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AI_REPLY_ENABLED, true)
    }

    fun setAiReplyEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AI_REPLY_ENABLED, enabled).apply()
    }

    fun isGlobalEnabled(context: Context): Boolean = isAiReplyEnabled(context)

    fun setGlobalEnabled(context: Context, enabled: Boolean) {
        setAiReplyEnabled(context, enabled)
    }

    fun getThemeMode(context: Context): ThemeMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return ThemeMode.fromPreference(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.preferenceValue))
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.preferenceValue).apply()
        AppCompatDelegate.setDefaultNightMode(mode.appCompatMode)
    }

    fun applySavedThemeMode(context: Context) {
        AppCompatDelegate.setDefaultNightMode(getThemeMode(context).appCompatMode)
    }

    fun getAiConfig(context: Context): AiConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AiConfig(
            displayName = prefs.getString(KEY_DISPLAY_NAME, "나") ?: "나",
            persona = prefs.getString(KEY_PERSONA, "친절하고 짧게 핵심만 답장합니다.")
                ?: "친절하고 짧게 핵심만 답장합니다.",
            provider = prefs.getString(KEY_PROVIDER, "OpenAI") ?: "OpenAI",
            apiKeyMode = prefs.getString(KEY_API_KEY_MODE, "앱에 저장") ?: "앱에 저장",
            apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
            replyMode = prefs.getString(KEY_REPLY_MODE, "간결하게") ?: "간결하게",
            triggerMode = prefs.getString(KEY_TRIGGER_MODE, "AI가 판단") ?: "AI가 판단"
        )
    }

    fun saveAiConfig(context: Context, config: AiConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_DISPLAY_NAME, config.displayName)
            .putString(KEY_PERSONA, config.persona)
            .putString(KEY_PROVIDER, config.provider)
            .putString(KEY_API_KEY_MODE, config.apiKeyMode)
            .putString(KEY_API_KEY, config.apiKey)
            .putString(KEY_REPLY_MODE, config.replyMode)
            .putString(KEY_TRIGGER_MODE, config.triggerMode)
            .apply()
    }

    fun getRoomTargets(context: Context): List<RoomTarget> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ROOM_TARGETS, null).orEmpty()
        if (raw.isBlank()) return emptyList()

        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val name = item.optString("name").trim()
                    if (name.isBlank()) continue
                    add(
                        RoomTarget(
                            name = name,
                            isEnabled = item.optBoolean("enabled", true),
                            lastImportedAt = item.optLong("lastImportedAt", 0L),
                            lastImportSource = item.optString("lastImportSource").ifBlank { null }
                        )
                    )
                }
            }.sortedBy { it.name.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addRoomTarget(context: Context, roomName: String): Boolean {
        val normalized = roomName.trim()
        if (normalized.isBlank()) return false

        val rooms = getRoomTargets(context).toMutableList()
        if (rooms.any { it.name.equals(normalized, ignoreCase = true) }) {
            return false
        }

        rooms.add(RoomTarget(normalized, true, 0L, null))
        saveRoomTargets(context, rooms)
        return true
    }

    fun removeRoomTarget(context: Context, roomName: String) {
        val normalized = roomName.trim()
        val rooms = getRoomTargets(context)
            .filterNot { it.name.equals(normalized, ignoreCase = true) }
        saveRoomTargets(context, rooms)
        removeRoomMemory(context, normalized)
    }

    fun setRoomTargetEnabled(context: Context, roomName: String, enabled: Boolean) {
        val normalized = roomName.trim()
        val rooms = getRoomTargets(context).map { room ->
            if (room.name.equals(normalized, ignoreCase = true)) {
                room.copy(isEnabled = enabled)
            } else {
                room
            }
        }
        saveRoomTargets(context, rooms)
    }

    fun getRoomTarget(context: Context, roomName: String): RoomTarget? {
        val normalized = roomName.trim()
        return getRoomTargets(context).firstOrNull { it.name.equals(normalized, ignoreCase = true) }
    }

    fun getRoomMemory(context: Context, roomName: String): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ROOM_MEMORIES, null).orEmpty()
        if (raw.isBlank()) return ""

        return try {
            val json = JSONObject(raw)
            json.optString(roomName.trim(), "")
        } catch (_: Exception) {
            ""
        }
    }

    fun saveRoomMemory(context: Context, roomName: String, memory: String) {
        val normalized = roomName.trim()
        if (normalized.isBlank()) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = try {
            JSONObject(prefs.getString(KEY_ROOM_MEMORIES, null).orEmpty().ifBlank { "{}" })
        } catch (_: Exception) {
            JSONObject()
        }

        json.put(normalized, memory.trim())
        prefs.edit().putString(KEY_ROOM_MEMORIES, json.toString()).apply()
    }

    fun importRoomHistory(
        context: Context,
        roomName: String,
        sourceLabel: String,
        csvText: String
    ): Int {
        val normalized = roomName.trim()
        if (normalized.isBlank()) return 0

        val cleanedLines = csvText.lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .take(300)
            .toList()
        if (cleanedLines.isEmpty()) return 0

        val existing = getRoomMemory(context, normalized).trim()
        val importedBlock = buildString {
            append("[CSV 가져오기: ")
            append(sourceLabel.ifBlank { "history.csv" })
            append("]\n")
            append(cleanedLines.joinToString("\n"))
        }

        val merged = buildString {
            if (existing.isNotBlank()) {
                append(existing)
                append("\n\n")
            }
            append(importedBlock)
        }.take(20000)

        saveRoomMemory(context, normalized, merged)
        upsertImportMetadata(context, normalized, sourceLabel.ifBlank { "history.csv" })
        return cleanedLines.size
    }

    fun getMostRecentImportedRoom(context: Context): RoomTarget? {
        return getRoomTargets(context)
            .filter { it.lastImportedAt > 0L }
            .maxByOrNull { it.lastImportedAt }
    }

    fun markRoomImported(context: Context, roomName: String, sourceLabel: String) {
        val normalized = roomName.trim()
        if (normalized.isBlank()) return
        upsertImportMetadata(context, normalized, sourceLabel.ifBlank { "history.csv" })
    }

    private fun saveRoomTargets(context: Context, rooms: List<RoomTarget>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        rooms.sortedBy { it.name.lowercase() }.forEach { room ->
            val item = JSONObject()
            item.put("name", room.name)
            item.put("enabled", room.isEnabled)
            item.put("lastImportedAt", room.lastImportedAt)
            item.put("lastImportSource", room.lastImportSource ?: "")
            array.put(item)
        }
        prefs.edit().putString(KEY_ROOM_TARGETS, array.toString()).apply()
    }

    private fun removeRoomMemory(context: Context, roomName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = try {
            JSONObject(prefs.getString(KEY_ROOM_MEMORIES, null).orEmpty().ifBlank { "{}" })
        } catch (_: Exception) {
            JSONObject()
        }
        json.remove(roomName)
        prefs.edit().putString(KEY_ROOM_MEMORIES, json.toString()).apply()
    }

    private fun upsertImportMetadata(context: Context, roomName: String, sourceLabel: String) {
        val now = System.currentTimeMillis()
        val rooms = getRoomTargets(context).toMutableList()
        val index = rooms.indexOfFirst { it.name.equals(roomName, ignoreCase = true) }

        if (index >= 0) {
            rooms[index] = rooms[index].copy(
                lastImportedAt = now,
                lastImportSource = sourceLabel
            )
        } else {
            rooms.add(RoomTarget(roomName, true, now, sourceLabel))
        }

        saveRoomTargets(context, rooms)
    }
}
