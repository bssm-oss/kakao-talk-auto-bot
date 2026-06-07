package com.example.kakaotalkautobot

object LogPrivacy {
    fun redact(logs: String): String {
        return logs.lineSequence()
            .map { redactLine(it) }
            .joinToString("\n")
            .let { if (logs.endsWith("\n")) "$it\n" else it }
    }

    internal fun redactLine(line: String): String {
        return line
            .replace(Regex("\\[[^\\]\\n]{1,40}]\\s*([^:]{1,40}):\\s*.*$")) { match ->
                val prefix = match.value.substringBefore(":")
                "$prefix: <메시지 숨김>"
            }
            .replace(Regex("\\[[^\\]\\n]{1,40}]\\s+(.{1,120})$")) { match ->
                if (match.value.contains("reason=") || match.value.contains("실패") || match.value.contains("스킵")) {
                    match.value
                } else {
                    match.value.substringBeforeLast(" ") + " <내용 숨김>"
                }
            }
    }
}
