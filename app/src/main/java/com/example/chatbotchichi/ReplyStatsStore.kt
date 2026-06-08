package com.example.kakaotalkautobot

import android.content.Context
import org.json.JSONObject

object ReplyStatsStore {
    private const val PREFS_NAME = "ReplyStatsPrefs"
    private const val KEY_COUNTS = "counts"

    data class Snapshot(
        val incoming: Int,
        val sent: Int,
        val skipped: Int,
        val failed: Int,
        val failureReasons: Map<String, Int>,
        val skipReasons: Map<String, Int>,
        val lastSentAtMillis: Long = 0L,
        val lastFailureReason: String? = null,
        val lastFailureAtMillis: Long = 0L,
        val lastSkipReason: String? = null,
        val lastSkipAtMillis: Long = 0L
    ) {
        fun summary(): String {
            val topFailure = failureReasons.maxByOrNull { it.value }
            val topSkip = skipReasons.maxByOrNull { it.value }
            val deliveryAttempts = sent + failed
            val deliveryRateText = if (deliveryAttempts > 0) {
                val rate = sent * 100 / deliveryAttempts
                "전송성공 $rate%"
            } else {
                "전송시도 없음"
            }
            val reasonText = when {
                topFailure != null -> "최다 실패: ${topFailure.key} ${topFailure.value}회"
                topSkip != null -> "최다 스킵: ${topSkip.key} ${topSkip.value}회"
                else -> "원인 없음"
            }
            return "수신 $incoming · 전송 $sent · 스킵 $skipped · 실패 $failed · $deliveryRateText · $reasonText"
        }

        fun detailSummary(limit: Int = 3): String {
            val failures = reasonSummary("실패", failureReasons, limit)
            val skips = reasonSummary("스킵", skipReasons, limit)
            val recent = recentReasonSummary()
            val timeline = recentTimelineSummary()
            return listOf(failures, skips, recent)
                .plus(timeline)
                .filter { it.isNotBlank() }
                .ifEmpty { listOf("원인 없음") }
                .joinToString(" · ")
        }

        private fun reasonSummary(label: String, reasons: Map<String, Int>, limit: Int): String {
            if (reasons.isEmpty()) return ""
            val text = reasons.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(limit.coerceAtLeast(1))
                .joinToString(", ") { "${it.key} ${it.value}회" }
            return "$label: $text"
        }

        private fun recentReasonSummary(): String {
            val parts = buildList {
                if (!lastFailureReason.isNullOrBlank()) add("최근 실패: $lastFailureReason")
                if (!lastSkipReason.isNullOrBlank()) add("최근 스킵: $lastSkipReason")
            }
            return parts.joinToString(", ")
        }

        private fun recentTimelineSummary(): String {
            val parts = buildList {
                if (lastSentAtMillis > 0L) add("전송 ${formatAge(lastSentAtMillis)}")
                if (lastFailureAtMillis > 0L) add("실패 ${formatAge(lastFailureAtMillis)}")
                if (lastSkipAtMillis > 0L) add("스킵 ${formatAge(lastSkipAtMillis)}")
            }
            if (parts.isEmpty()) return ""
            return "최근 이벤트: ${parts.joinToString(", ")}"
        }
    }

    @Synchronized
    fun record(context: Context, label: String, reason: String? = null) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val root = readCounts(context)
        val key = when (label) {
            "IN" -> "incoming"
            "OUT" -> "sent"
            "OUT_SKIP" -> "skipped"
            "OUT_FAIL" -> "failed"
            else -> return
        }
        root.put(key, root.optInt(key, 0) + 1)
        when (label) {
            "OUT" -> {
                root.put("lastSentAtMillis", System.currentTimeMillis())
            }
            "OUT_FAIL" -> {
                val normalizedReason = incrementReason(root, "failureReasons", reason)
                root.put("lastFailureReason", normalizedReason)
                root.put("lastFailureAtMillis", System.currentTimeMillis())
            }
            "OUT_SKIP" -> {
                val normalizedReason = incrementReason(root, "skipReasons", reason)
                root.put("lastSkipReason", normalizedReason)
                root.put("lastSkipAtMillis", System.currentTimeMillis())
            }
        }
        prefs.edit().putString(KEY_COUNTS, root.toString()).apply()
    }

    fun snapshot(context: Context): Snapshot {
        val root = readCounts(context)
        return Snapshot(
            incoming = root.optInt("incoming", 0),
            sent = root.optInt("sent", 0),
            skipped = root.optInt("skipped", 0),
            failed = root.optInt("failed", 0),
            failureReasons = readReasonMap(root, "failureReasons"),
            skipReasons = readReasonMap(root, "skipReasons"),
            lastSentAtMillis = root.optLong("lastSentAtMillis", 0L),
            lastFailureReason = root.optString("lastFailureReason").ifBlank { null },
            lastFailureAtMillis = root.optLong("lastFailureAtMillis", 0L),
            lastSkipReason = root.optString("lastSkipReason").ifBlank { null },
            lastSkipAtMillis = root.optLong("lastSkipAtMillis", 0L)
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_COUNTS)
            .apply()
    }

    private fun readCounts(context: Context): JSONObject {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return try {
            JSONObject(prefs.getString(KEY_COUNTS, null).orEmpty().ifBlank { "{}" })
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun incrementReason(root: JSONObject, key: String, reason: String?): String {
        val reasons = root.optJSONObject(key) ?: JSONObject()
        val normalizedReason = normalizeReason(reason)
        reasons.put(normalizedReason, reasons.optInt(normalizedReason, 0) + 1)
        root.put(key, reasons)
        return normalizedReason
    }

    internal fun normalizeReason(reason: String?): String {
        val normalized = reason
            ?.trim()
            ?.ifBlank { null }
            ?: return "unknown"
        val lowered = normalized.lowercase()
        return when {
            lowered.contains(SessionReplier.REASON_NO_SESSION.lowercase()) -> SessionReplier.REASON_NO_SESSION
            lowered.contains(SessionReplier.REASON_NO_REMOTE_INPUT.lowercase()) -> SessionReplier.REASON_NO_REMOTE_INPUT
            lowered.contains(SessionReplier.REASON_PENDING_INTENT_NULL.lowercase()) -> SessionReplier.REASON_PENDING_INTENT_NULL
            lowered.contains(SessionReplier.REASON_PENDING_INTENT_SEND_FAILED.lowercase()) -> SessionReplier.REASON_PENDING_INTENT_SEND_FAILED
            lowered.contains(SessionReplier.REASON_EXCEPTION.lowercase()) -> SessionReplier.REASON_EXCEPTION
            lowered.contains("send_failed_after_generation") -> "send_failed_after_generation"
            lowered.contains("off") || normalized.contains("답장 OFF") -> "reply off"
            normalized.contains("의미 없는 짧은 메시지") -> "low signal"
            normalized.contains("응답 조건") || normalized.contains("조건을 충족") -> "condition not met"
            normalized.contains("모델이 로드되지") || lowered.contains("model") && lowered.contains("load") -> "model not loaded"
            normalized.contains("전송 가능한 품질") || lowered.contains("quality") && lowered.contains("candidate") -> "ai quality rejected"
            normalized.contains("AI 응답 생성 중 오류") || lowered.contains("generation") && lowered.contains("exception") -> "ai generation exception"
            normalized.contains("고정 답장 목록") || normalized.contains("고정 답장 템플릿") -> "canned reply empty"
            normalized.contains("빈 메시지") -> "blank message"
            else -> normalized.take(80)
        }
    }

    private fun readReasonMap(root: JSONObject, key: String): Map<String, Int> {
        val reasons = root.optJSONObject(key) ?: JSONObject()
        return buildMap {
            val keys = reasons.keys()
            while (keys.hasNext()) {
                val reason = keys.next()
                put(reason, reasons.optInt(reason, 0))
            }
        }
    }

    internal fun formatAge(eventAtMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (eventAtMillis <= 0L) return "없음"
        val elapsedSeconds = ((nowMillis - eventAtMillis).coerceAtLeast(0L) / 1000L).toInt()
        return when {
            elapsedSeconds < 60 -> "${elapsedSeconds}초 전"
            elapsedSeconds < 3600 -> "${elapsedSeconds / 60}분 전"
            elapsedSeconds < 86400 -> "${elapsedSeconds / 3600}시간 전"
            else -> "${elapsedSeconds / 86400}일 전"
        }
    }
}
