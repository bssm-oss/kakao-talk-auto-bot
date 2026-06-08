package com.example.kakaotalkautobot

object LogPrivacy {
    private val structuredLogPrefix = Regex("^\\[[^\\]\\n]+]\\[[A-Z_]+]")
    private val incomingPattern = Regex("^(\\[[^\\]\\n]+]\\[[A-Z_]+])\\s*\\[[^\\]\\n]{1,80}]\\s*([^:\\n]{1,80}):\\s*(.*)$")
    private val roomOnlyPattern = Regex("^(\\[[^\\]\\n]+]\\[[A-Z_]+]\\s*(?:❌\\s*|⏭\\s*)?)\\[[^\\]\\n]{1,80}]\\s*(.*)$")
    private val looseChatLinePattern = Regex("^(?:.*?(?:room|chat|kakao|카카오|카톡).*?[:=]?\\s*)?\\[[^\\]\\n]{1,80}]\\s*([^:\\n]{1,80}):\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val keyValueChatPattern = Regex("(^|[\\s,;])(room|sender|speaker|message|msg|text|방|발화자|메시지)\\s*=\\s*([^,\\]\\n]+)", RegexOption.IGNORE_CASE)
    private val jsonChatFieldPattern = Regex("(\"(?:room|sender|speaker|message|msg|text|방|발화자|메시지)\"\\s*:\\s*\")([^\"\\n]*)\"", RegexOption.IGNORE_CASE)
    private val reasonPattern = Regex("(\\(reason=[^)]+\\)|reason=[^\\s)]+|send_failed_after_generation:[^\\s)]+)")
    private val emailPattern = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val urlPattern = Regex("https?://[^\\s)]+", RegexOption.IGNORE_CASE)
    private val phonePattern = Regex("\\b(?:\\+?82[-\\s]?)?0?1[016789][-\\s]?\\d{3,4}[-\\s]?\\d{4}\\b")

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

        if (!structuredLogPrefix.containsMatchIn(line)) {
            looseChatLinePattern.matchEntire(line)?.let {
                return "<카톡 로그 숨김>${reasonSuffix(line)}"
            }
        }

        if (keyValueChatPattern.containsMatchIn(line)) {
            return redactChatKeyValues(line)
        }

        if (jsonChatFieldPattern.containsMatchIn(line)) {
            return redactJsonChatFields(line)
        }

        return redactSensitiveTokens(line)
    }

    private fun reasonSuffix(text: String): String {
        val reasons = reasonPattern.findAll(text)
            .map { redactSensitiveTokens(it.value) }
            .distinct()
            .toList()
        return if (reasons.isEmpty()) "" else " ${reasons.joinToString(" ")}"
    }

    internal fun redactSensitiveTokens(text: String): String {
        return text
            .replace(urlPattern, "<URL 숨김>")
            .replace(emailPattern, "<이메일 숨김>")
            .replace(phonePattern, "<전화번호 숨김>")
    }

    private fun redactChatKeyValues(line: String): String {
        return redactSensitiveTokens(
            keyValueChatPattern.replace(line) { match ->
                val prefix = match.groupValues[1]
                val key = match.groupValues[2]
                val replacement = when (key.lowercase()) {
                    "room", "chat", "방" -> "<방 숨김>"
                    "sender", "speaker", "발화자" -> "<발화자 숨김>"
                    "message", "msg", "text", "메시지" -> "<메시지 숨김>"
                    else -> "<내용 숨김>"
                }
                "$prefix$key=$replacement"
            }
        )
    }

    private fun redactJsonChatFields(line: String): String {
        return redactSensitiveTokens(
            jsonChatFieldPattern.replace(line) { match ->
                val prefix = match.groupValues[1]
                val key = prefix.substringAfter('"').substringBefore('"').lowercase()
                val replacement = when (key) {
                    "room", "방" -> "<방 숨김>"
                    "sender", "speaker", "발화자" -> "<발화자 숨김>"
                    "message", "msg", "text", "메시지" -> "<메시지 숨김>"
                    else -> "<내용 숨김>"
                }
                "$prefix$replacement\""
            }
        )
    }
}
