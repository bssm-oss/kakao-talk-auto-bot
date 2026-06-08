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
        val skipReasons: Map<String, Int>
    ) {
        fun summary(): String {
            val topFailure = failureReasons.maxByOrNull { it.value }
            val topSkip = skipReasons.maxByOrNull { it.value }
            val reasonText = when {
                topFailure != null -> "최다 실패: ${topFailure.key} ${topFailure.value}회"
                topSkip != null -> "최다 스킵: ${topSkip.key} ${topSkip.value}회"
                else -> "원인 없음"
            }
            return "수신 $incoming · 전송 $sent · 스킵 $skipped · 실패 $failed · $reasonText"
        }

        fun detailSummary(limit: Int = 3): String {
            val failures = reasonSummary("실패", failureReasons, limit)
            val skips = reasonSummary("스킵", skipReasons, limit)
            return listOf(failures, skips)
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
            "OUT_FAIL" -> incrementReason(root, "failureReasons", reason)
            "OUT_SKIP" -> incrementReason(root, "skipReasons", reason)
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
            skipReasons = readReasonMap(root, "skipReasons")
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

    private fun incrementReason(root: JSONObject, key: String, reason: String?) {
        val reasons = root.optJSONObject(key) ?: JSONObject()
        val normalizedReason = normalizeReason(reason)
        reasons.put(normalizedReason, reasons.optInt(normalizedReason, 0) + 1)
        root.put(key, reasons)
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
}
