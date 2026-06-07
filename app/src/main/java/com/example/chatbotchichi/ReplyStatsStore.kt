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
        val failureReasons: Map<String, Int>
    ) {
        fun summary(): String {
            val topFailure = failureReasons.maxByOrNull { it.value }
            val failureText = if (topFailure == null) {
                "실패 원인 없음"
            } else {
                "최다 실패: ${topFailure.key} ${topFailure.value}회"
            }
            return "수신 $incoming · 전송 $sent · 스킵 $skipped · 실패 $failed · $failureText"
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
        if (label == "OUT_FAIL") {
            val failures = root.optJSONObject("failureReasons") ?: JSONObject()
            val normalizedReason = reason?.trim()?.ifBlank { null } ?: "unknown"
            failures.put(normalizedReason, failures.optInt(normalizedReason, 0) + 1)
            root.put("failureReasons", failures)
        }
        prefs.edit().putString(KEY_COUNTS, root.toString()).apply()
    }

    fun snapshot(context: Context): Snapshot {
        val root = readCounts(context)
        val failures = root.optJSONObject("failureReasons") ?: JSONObject()
        val failureReasons = buildMap {
            val keys = failures.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, failures.optInt(key, 0))
            }
        }
        return Snapshot(
            incoming = root.optInt("incoming", 0),
            sent = root.optInt("sent", 0),
            skipped = root.optInt("skipped", 0),
            failed = root.optInt("failed", 0),
            failureReasons = failureReasons
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
}
