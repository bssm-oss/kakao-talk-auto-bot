package com.example.kakaotalkautobot

import android.content.Context
import org.json.JSONObject
import java.util.Locale

object AutoMemoryStore {
    private const val PREFS_NAME = "AutoMemoryPrefs"
    private const val KEY_MEMORIES = "auto_memories"
    private val urlRegex = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
    private val keywordRegex = Regex("""[A-Za-z0-9가-힣._@-]{2,}""")
    private val stopwords = setOf(
        "그리고", "그러면", "그냥", "이거", "저거", "해서", "하면", "하면돼", "진짜",
        "지금", "방금", "이제", "그거", "좋다", "좋아", "응", "네", "ㅋㅋ", "ㅎㅎ",
        "the", "and", "for", "with", "this", "that", "from"
    )

    fun refresh(context: Context, room: String, history: List<RoomHistoryMessage>) {
        val normalizedRoom = room.trim()
        if (normalizedRoom.isBlank()) return

        val summary = buildSummary(history)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val root = parseRoot(prefs)
        if (summary.isBlank()) {
            root.remove(normalizedRoom)
        } else {
            root.put(
                normalizedRoom,
                JSONObject()
                    .put("summary", summary)
                    .put("updatedAt", System.currentTimeMillis())
            )
        }
        prefs.edit().putString(KEY_MEMORIES, root.toString()).apply()
    }

    fun getSummary(context: Context, room: String): String {
        val normalizedRoom = room.trim()
        if (normalizedRoom.isBlank()) return ""
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val root = parseRoot(prefs)
        return root.optJSONObject(normalizedRoom)?.optString("summary", "").orEmpty()
    }

    internal fun buildSummary(history: List<RoomHistoryMessage>): String {
        val incomingMessages = history
            .filter { it.incoming }
            .filter { it.message.isNotBlank() }
        if (incomingMessages.isEmpty()) return ""

        val participantSummary = incomingMessages
            .groupBy { it.sender.trim().ifBlank { "알수없음" } }
            .entries
            .sortedByDescending { it.value.size }
            .take(4)
            .joinToString("; ") { (sender, messages) ->
                "$sender(${describeSender(messages)})"
            }

        val topKeywords = incomingMessages
            .flatMap { extractKeywords(it.message) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(6)
            .map { it.key }

        val topDomains = incomingMessages
            .flatMap { extractDomains(it.message) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(4)
            .map { it.key }

        return buildString {
            append("자동 메모리 요약\n")
            append("- 참여자 성향: ")
            append(participantSummary.ifBlank { "최근 참여자 정보가 부족함" })
            if (topKeywords.isNotEmpty()) {
                append("\n- 최근 주제 키워드: ")
                append(topKeywords.joinToString(", "))
            }
            if (topDomains.isNotEmpty()) {
                append("\n- 최근 공유 링크: ")
                append(topDomains.joinToString(", "))
            }
        }
    }

    private fun describeSender(messages: List<RoomHistoryMessage>): String {
        val averageLength = messages.map { it.message.length }.average()
        val questionCount = messages.count { it.message.contains("?") || it.message.contains("？") }
        val linkCount = messages.count { urlRegex.containsMatchIn(it.message) }
        val laughCount = messages.count { it.message.contains("ㅋ") || it.message.contains("ㅎ") }

        val traits = mutableListOf<String>()
        if (questionCount >= 2 || questionCount * 2 >= messages.size) {
            traits += "질문이 많음"
        }
        if (linkCount >= 1) {
            traits += "링크 공유가 잦음"
        }
        if (laughCount >= 1) {
            traits += "가벼운 말투"
        }
        if (averageLength <= 8.0) {
            traits += "짧게 반응함"
        } else if (averageLength >= 24.0) {
            traits += "길게 설명함"
        }
        return traits.distinct().take(2).joinToString(", ").ifBlank { "일반 참여자" }
    }

    private fun extractKeywords(message: String): List<String> {
        return keywordRegex.findAll(message)
            .map { it.value.lowercase(Locale.ROOT) }
            .filterNot { it in stopwords }
            .filterNot { it.startsWith("http") }
            .filterNot { it.length <= 2 && it.all(Char::isDigit) }
            .toList()
    }

    private fun extractDomains(message: String): List<String> {
        return urlRegex.findAll(message).mapNotNull { match ->
            runCatching {
                java.net.URI(match.value).host?.removePrefix("www.")
            }.getOrNull()
        }.toList()
    }

    private fun parseRoot(prefs: android.content.SharedPreferences): JSONObject {
        return try {
            JSONObject(prefs.getString(KEY_MEMORIES, null).orEmpty().ifBlank { "{}" })
        } catch (_: Exception) {
            JSONObject()
        }
    }
}
