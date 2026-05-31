package com.example.kakaotalkautobot

import android.content.Context
import org.json.JSONObject

object StyleProfileStore {
    private const val PREFS_NAME = "StyleProfilePrefs"
    private const val KEY_USER_LEARNED_ENABLED = "user_learned_style_enabled"
    private const val KEY_USER_LEARNED_OVERRIDE = "user_learned_style_override"
    private const val KEY_ROOM_PROFILES = "room_style_profiles"
    private const val DIRECT_EXAMPLE_LIMIT = 260
    private const val MANUAL_ROOM_STYLE_LIMIT = 220
    private const val LEARNED_STYLE_LIMIT = 220

    data class LearnedStyleState(
        val enabled: Boolean,
        val override: String,
        val generated: String
    ) {
        val effective: String
            get() = if (!enabled) "" else override.trim().ifBlank { generated.trim() }
    }

    data class StyleGuideParts(
        val personaExamples: String = "",
        val manualRoomStyle: String = "",
        val learnedUserStyle: String = "",
        val learnedRoomStyle: String = ""
    )

    fun buildPromptStyleGuide(
        context: Context,
        aiConfig: AppSettings.AiConfig,
        config: AutoReplyConfig,
        room: String,
        history: List<RoomHistoryMessage>
    ): String {
        val displayName = aiConfig.displayName.ifBlank { "나" }
        val learnedUserStyle = getUserLearnedStyleState(
            context = context,
            displayName = displayName,
            history = history,
            importedText = config.roomMemory
        ).effective
        val learnedRoomStyle = getRoomLearnedStyleState(context, room, history).effective

        return composePromptStyleGuide(
            StyleGuideParts(
                personaExamples = aiConfig.personaExamples,
                manualRoomStyle = config.roomStyle,
                learnedUserStyle = learnedUserStyle,
                learnedRoomStyle = learnedRoomStyle
            )
        )
    }

    fun getUserLearnedStyleState(
        context: Context,
        displayName: String,
        history: List<RoomHistoryMessage>,
        importedText: String = ""
    ): LearnedStyleState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return LearnedStyleState(
            enabled = prefs.getBoolean(KEY_USER_LEARNED_ENABLED, true),
            override = prefs.getString(KEY_USER_LEARNED_OVERRIDE, "").orEmpty(),
            generated = buildUserStyleFromMessages(displayName, history, importedText)
        )
    }

    fun setUserLearnedStyleEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_USER_LEARNED_ENABLED, enabled)
            .apply()
    }

    fun saveUserLearnedStyleOverride(context: Context, style: String) {
        val cleaned = style.trim()
        val edit = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        if (cleaned.isBlank()) {
            edit.remove(KEY_USER_LEARNED_OVERRIDE)
        } else {
            edit.putString(KEY_USER_LEARNED_OVERRIDE, cleaned)
        }
        edit.apply()
    }

    fun resetUserLearnedStyle(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_USER_LEARNED_OVERRIDE)
            .apply()
    }

    fun getRoomLearnedStyleState(
        context: Context,
        room: String,
        history: List<RoomHistoryMessage>
    ): LearnedStyleState {
        val normalizedRoom = room.trim()
        val profile = getRoomProfile(context, normalizedRoom)
        return LearnedStyleState(
            enabled = profile.optBoolean("enabled", true),
            override = profile.optString("override", ""),
            generated = buildRoomStyleFromMessages(history)
        )
    }

    fun setRoomLearnedStyleEnabled(context: Context, room: String, enabled: Boolean) {
        updateRoomProfile(context, room) { profile ->
            profile.put("enabled", enabled)
        }
    }

    fun saveRoomLearnedStyleOverride(context: Context, room: String, style: String) {
        updateRoomProfile(context, room) { profile ->
            val cleaned = style.trim()
            if (cleaned.isBlank()) {
                profile.remove("override")
            } else {
                profile.put("override", cleaned)
            }
        }
    }

    fun resetRoomLearnedStyle(context: Context, room: String) {
        updateRoomProfile(context, room) { profile ->
            profile.remove("override")
        }
    }

    internal fun composePromptStyleGuide(parts: StyleGuideParts): String {
        val userExamples = compactBlock(parts.personaExamples, DIRECT_EXAMPLE_LIMIT)
        val manualRoomStyle = compactBlock(parts.manualRoomStyle, MANUAL_ROOM_STYLE_LIMIT)
        val learnedUserStyle = compactBlock(parts.learnedUserStyle, LEARNED_STYLE_LIMIT)
        val learnedRoomStyle = compactBlock(parts.learnedRoomStyle, LEARNED_STYLE_LIMIT)

        if (
            userExamples.isBlank() &&
            manualRoomStyle.isBlank() &&
            learnedUserStyle.isBlank() &&
            learnedRoomStyle.isBlank()
        ) {
            return ""
        }

        return buildString {
            append("말투/스타일 지침:\n")
            append("- 우선순위: 사용자 직접 예시 > 수동 방 스타일 > 가져온 내 발화 스타일 > 앱에서 관측한 내 발화 스타일 > 자동 방 스타일 > 최근 대화 사실.\n")
            append("- 답장할지 여부는 트리거 모드를 따르되, 답장을 만들 때는 위 우선순위의 말투를 먼저 따른다.\n")
            if (userExamples.isNotBlank()) {
                append("사용자 직접 예시:\n")
                append(userExamples)
                append("\n")
            }
            if (manualRoomStyle.isNotBlank()) {
                append("수동 방 스타일:\n")
                append(manualRoomStyle)
                append("\n")
            }
            if (learnedUserStyle.isNotBlank()) {
                append("학습된 사용자 말투:\n")
                append(learnedUserStyle)
                append("\n")
            }
            if (learnedRoomStyle.isNotBlank()) {
                append("학습된 방 말투:\n")
                append(learnedRoomStyle)
                append("\n")
            }
        }.trim()
    }

    internal fun buildUserStyleFromMessages(
        displayName: String,
        history: List<RoomHistoryMessage>,
        importedText: String = ""
    ): String {
        val normalizedDisplayName = displayName.trim()
        if (normalizedDisplayName.isBlank()) return ""

        val importedOwnMessages = extractOwnMessagesFromText(normalizedDisplayName, importedText)
        val observedOwnMessages = history
            .filter { message ->
                message.sender.equals(normalizedDisplayName, ignoreCase = true) ||
                    (!message.incoming && message.sender.equals("나", ignoreCase = true))
            }
            .map { it.message.trim() }
            .filter { it.isNotBlank() && !it.equals("AI", ignoreCase = true) }

        val messages = (importedOwnMessages + observedOwnMessages)
            .map { sanitizeExample(it) }
            .filter { it.isNotBlank() }
            .takeLast(12)

        return describeStyle("내 발화", messages)
    }

    internal fun buildRoomStyleFromMessages(history: List<RoomHistoryMessage>): String {
        val messages = history
            .map { sanitizeExample(it.message) }
            .filter { it.isNotBlank() }
            .takeLast(24)
        if (messages.isEmpty()) return ""

        val formalCount = messages.count { isFormal(it) }
        val laughCount = messages.count { it.contains("ㅋ") || it.contains("ㅎ") }
        val exclamationCount = messages.count { it.contains("!") }
        val averageLength = messages.map { it.length }.average()

        val roomTone = if (formalCount * 2 >= messages.size) {
            "학교/팀방처럼 존댓말과 격식이 중심"
        } else if (laughCount >= 2 || averageLength <= 14.0) {
            "친한 친구방처럼 가볍고 짧은 반응이 중심"
        } else {
            "일상 대화처럼 너무 딱딱하지 않은 중간 톤"
        }
        val density = when {
            averageLength <= 12.0 -> "짧은 한마디 위주"
            averageLength >= 32.0 -> "맥락을 붙이는 설명형"
            else -> "한두 문장 위주"
        }
        val punctuation = when {
            exclamationCount >= 2 -> "감탄사를 자연스럽게 쓰는 편"
            laughCount >= 2 -> "웃음 표현을 가볍게 섞는 편"
            else -> "문장부호는 과하지 않게 쓰는 편"
        }
        val examples = messages.takeLast(3).joinToString(" / ") { "\"${it.take(36)}\"" }

        return "방 전체 말투 기준: $roomTone, $density, $punctuation. 최근 예시: $examples"
    }

    private fun describeStyle(label: String, messages: List<String>): String {
        if (messages.isEmpty()) return ""

        val formalCount = messages.count { isFormal(it) }
        val questionCount = messages.count { it.endsWith("?") || it.endsWith("？") }
        val laughCount = messages.count { it.contains("ㅋ") || it.contains("ㅎ") }
        val averageLength = messages.map { it.length }.average()

        val tone = if (formalCount * 2 >= messages.size) {
            "존댓말/격식 표현을 자주 씀"
        } else {
            "반말과 캐주얼한 표현을 자주 씀"
        }
        val density = when {
            averageLength <= 12.0 -> "짧게 한마디로 답하는 편"
            averageLength >= 28.0 -> "필요할 때 설명을 붙이는 편"
            else -> "한두 문장으로 답하는 편"
        }
        val interaction = when {
            questionCount >= 2 -> "질문으로 대화를 이어가는 편"
            laughCount >= 2 -> "웃음 표현을 가볍게 섞는 편"
            else -> "확인/응답 중심으로 말하는 편"
        }
        val examples = messages.takeLast(3).joinToString(" / ") { "\"${it.take(36)}\"" }

        return "$label 기준: $tone, $density, $interaction. 최근 예시: $examples"
    }

    private fun extractOwnMessagesFromText(displayName: String, rawText: String): List<String> {
        if (rawText.isBlank()) return emptyList()
        return rawText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val pipeParts = line.split("|").map { it.trim() }
                if (pipeParts.size >= 3 && pipeParts[pipeParts.size - 2].equals(displayName, ignoreCase = true)) {
                    return@mapNotNull pipeParts.last()
                }

                val colonIndex = line.indexOf(":")
                if (colonIndex in 1..50) {
                    val sender = line.substring(0, colonIndex).trim()
                    if (sender.equals(displayName, ignoreCase = true)) {
                        return@mapNotNull line.substring(colonIndex + 1).trim()
                    }
                }
                null
            }
            .toList()
    }

    private fun compactBlock(text: String, limit: Int): String {
        return text
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .take(limit)
            .trim()
    }

    private fun sanitizeExample(message: String): String {
        return message
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)
    }

    private fun isFormal(message: String): Boolean {
        return message.endsWith("요") ||
            message.endsWith("습니다") ||
            message.endsWith("합니다") ||
            message.endsWith("입니다") ||
            message.contains("습니다") ||
            message.contains("합니다")
    }

    private fun getRoomProfile(context: Context, room: String): JSONObject {
        if (room.isBlank()) return JSONObject()
        val root = readRoomProfiles(context)
        return root.optJSONObject(room) ?: JSONObject()
    }

    private fun updateRoomProfile(context: Context, room: String, block: (JSONObject) -> Unit) {
        val normalizedRoom = room.trim()
        if (normalizedRoom.isBlank()) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val root = readRoomProfiles(context)
        val profile = root.optJSONObject(normalizedRoom) ?: JSONObject()
        block(profile)
        root.put(normalizedRoom, profile)
        prefs.edit().putString(KEY_ROOM_PROFILES, root.toString()).apply()
    }

    private fun readRoomProfiles(context: Context): JSONObject {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return try {
            JSONObject(prefs.getString(KEY_ROOM_PROFILES, null).orEmpty().ifBlank { "{}" })
        } catch (_: Exception) {
            JSONObject()
        }
    }
}
