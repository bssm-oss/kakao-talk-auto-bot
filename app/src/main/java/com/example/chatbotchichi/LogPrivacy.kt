package com.example.kakaotalkautobot

object LogPrivacy {
    private val incomingPattern = Regex("^(\\[[^\\]\\n]+]\\[[A-Z_]+])\\s*\\[[^\\]\\n]{1,80}]\\s*([^:\\n]{1,80}):\\s*(.*)$")
    private val roomOnlyPattern = Regex("^(\\[[^\\]\\n]+]\\[[A-Z_]+]\\s*(?:❌\\s*|⏭\\s*)?)\\[[^\\]\\n]{1,80}]\\s*(.*)$")
    private val reasonPattern = Regex("(\\(reason=[^)]+\\)|reason=[^\\s)]+|send_failed_after_generation:[^\\s)]+)")

    fun redact(logs: String): String {
        return logs.lineSequence()
            .map { redactLine(it) }
            .joinToString("\n")
            .let { if (logs.endsWith("\n")) "$it\n" else it }
    }

    internal fun redactLine(line: String): String {
        incomingPattern.matchEntire(line)?.let { match ->
            return "${match.groupValues[1]} [<방 숨김>] <발화자 숨김>: <메시지 숨김>${reasonSuffix(line)}"
        }

        roomOnlyPattern.matchEntire(line)?.let { match ->
            val detail = match.groupValues[2].trim()
            val safeDetail = when {
                detail.contains("reason=") -> "전송 실패 ${reasonSuffix(detail)}".trim()
                detail.contains("실패") -> "전송 실패 ${reasonSuffix(detail)}".trim()
                detail.contains("스킵") -> "스킵 ${reasonSuffix(detail)}".trim()
                detail.isBlank() -> "<내용 숨김>"
                else -> "<내용 숨김>"
            }
            return "${match.groupValues[1]}[<방 숨김>] $safeDetail"
        }

        return line
    }

    private fun reasonSuffix(text: String): String {
        val reasons = reasonPattern.findAll(text)
            .map { it.value }
            .distinct()
            .toList()
        return if (reasons.isEmpty()) "" else " ${reasons.joinToString(" ")}"
    }
}
