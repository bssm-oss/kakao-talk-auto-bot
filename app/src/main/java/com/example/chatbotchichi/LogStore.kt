package com.example.kakaotalkautobot

import android.content.Context
import java.io.File

object LogStore {
    private const val LOG_DIR = "logs"
    private const val LOG_FILE = "app.log"
    private const val MAX_LINES = 100
    const val DEFAULT_RETENTION_DAYS = 7
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    @Synchronized
    fun append(context: Context, line: String) {
        val file = logFile(context)
        pruneIfExpired(file)
        val lines = if (file.exists()) file.readLines().toMutableList() else mutableListOf()
        lines.add(line)
        if (lines.size > MAX_LINES) {
            val overflow = lines.size - MAX_LINES
            lines.subList(0, overflow).clear()
        }
        file.writeText(lines.joinToString("\n", postfix = "\n"))
    }

    fun appendWithTimestamp(context: Context, message: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        append(context, "[$time] $message")
    }

    fun getAll(context: Context): String {
        val file = logFile(context)
        pruneIfExpired(file)
        return if (file.exists()) file.readText() else ""
    }

    fun clear(context: Context) {
        val file = logFile(context)
        if (file.exists()) file.delete()
    }

    private fun logFile(context: Context): File {
        val dir = File(context.filesDir, LOG_DIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, LOG_FILE)
    }

    internal fun pruneIfExpired(
        file: File,
        nowMs: Long = System.currentTimeMillis(),
        retentionDays: Int = DEFAULT_RETENTION_DAYS
    ): Boolean {
        if (!file.exists()) return false
        val retentionMs = retentionDays.coerceAtLeast(1) * DAY_MS
        val ageMs = nowMs - file.lastModified()
        return if (ageMs > retentionMs) {
            file.delete()
        } else {
            false
        }
    }
}
