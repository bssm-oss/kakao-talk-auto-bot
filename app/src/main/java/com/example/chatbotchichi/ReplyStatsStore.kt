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
        val normalizedReason = reason?.trim()?.ifBlank { null } ?: "unknown"
        reasons.put(normalizedReason, reasons.optInt(normalizedReason, 0) + 1)
        root.put(key, reasons)
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
