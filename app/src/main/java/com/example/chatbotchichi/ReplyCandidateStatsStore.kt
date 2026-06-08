package com.example.kakaotalkautobot

import android.content.Context
import org.json.JSONObject

object ReplyCandidateStatsStore {
    private const val PREFS_NAME = "ReplyCandidateStatsPrefs"
    private const val KEY_COUNTS = "counts"
    private const val MIN_QUALITY_SCORE = 45
    private const val MIN_PRIOR_SAMPLE_COUNT = 5

    data class SourceStats(
        val generated: Int,
        val selected: Int,
        val blank: Int,
        val lowQuality: Int,
        val totalLatencyMs: Long,
        val maxLatencyMs: Long
    ) {
        val averageLatencyMs: Long
            get() = if (generated > 0) totalLatencyMs / generated else 0L
    }

    data class Snapshot(
        val sources: Map<String, SourceStats>
    ) {
        fun summary(limit: Int = 4): String {
            if (sources.isEmpty()) return "후보 통계 없음"
            return sources.entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, SourceStats>> { it.value.selected }
                        .thenByDescending { it.value.generated }
                        .thenBy { it.key }
                )
                .take(limit.coerceAtLeast(1))
                .joinToString(" · ") { (source, stats) ->
                    "$source 선택 ${stats.selected}/${stats.generated}, 빈 ${stats.blank}, 저품질 ${stats.lowQuality}, 평균 ${stats.averageLatencyMs}ms"
                }
        }

        fun selectionPriors(): Map<String, Int> {
            return sources.mapValues { (_, stats) ->
                stats.selectionPrior()
            }.filterValues { it != 0 }
        }
    }

    internal fun SourceStats.selectionPrior(): Int {
        if (generated < MIN_PRIOR_SAMPLE_COUNT) return 0
        val selectedRate = selected.toDouble() / generated
        val blankRate = blank.toDouble() / generated
        val lowQualityRate = lowQuality.toDouble() / generated
        return when {
            selectedRate >= 0.45 && blankRate <= 0.15 && lowQualityRate <= 0.25 -> 6
            selectedRate >= 0.30 && blankRate <= 0.20 && lowQualityRate <= 0.35 -> 3
            blankRate >= 0.35 || lowQualityRate >= 0.55 -> -6
            blankRate >= 0.25 || lowQualityRate >= 0.40 -> -3
            else -> 0
        }
    }

    @Synchronized
    fun recordBatch(
        context: Context,
        candidates: List<ReplyQualityEvaluator.Candidate>,
        selectedSource: String?
    ) {
        if (candidates.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val root = readCounts(context)
        candidates.forEach { candidate ->
            val source = normalizeSource(candidate.source)
            val stats = root.optJSONObject(source) ?: JSONObject()
            stats.put("generated", stats.optInt("generated", 0) + 1)
            if (source == normalizeSource(selectedSource)) {
                stats.put("selected", stats.optInt("selected", 0) + 1)
            }
            if (candidate.reply.isBlank() || candidate.reasons.contains("blank")) {
                stats.put("blank", stats.optInt("blank", 0) + 1)
            }
            if (candidate.score < MIN_QUALITY_SCORE) {
                stats.put("lowQuality", stats.optInt("lowQuality", 0) + 1)
            }
            val latencyMs = candidate.latencyMs.coerceAtLeast(0L)
            stats.put("totalLatencyMs", stats.optLong("totalLatencyMs", 0L) + latencyMs)
            stats.put("maxLatencyMs", maxOf(stats.optLong("maxLatencyMs", 0L), latencyMs))
            root.put(source, stats)
        }
        prefs.edit().putString(KEY_COUNTS, root.toString()).apply()
    }

    fun snapshot(context: Context): Snapshot {
        val root = readCounts(context)
        return snapshotFromJson(root)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_COUNTS)
            .apply()
    }

    internal fun snapshotFromJson(root: JSONObject): Snapshot {
        val sources = buildMap {
            val keys = root.keys()
            while (keys.hasNext()) {
                val source = keys.next()
                val stats = root.optJSONObject(source) ?: continue
                put(
                    source,
                    SourceStats(
                        generated = stats.optInt("generated", 0),
                        selected = stats.optInt("selected", 0),
                        blank = stats.optInt("blank", 0),
                        lowQuality = stats.optInt("lowQuality", 0),
                        totalLatencyMs = stats.optLong("totalLatencyMs", 0L),
                        maxLatencyMs = stats.optLong("maxLatencyMs", 0L)
                    )
                )
            }
        }
        return Snapshot(sources)
    }

    private fun readCounts(context: Context): JSONObject {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return try {
            JSONObject(prefs.getString(KEY_COUNTS, null).orEmpty().ifBlank { "{}" })
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun normalizeSource(source: String?): String {
        return source?.trim()?.ifBlank { null } ?: "unknown"
    }
}
