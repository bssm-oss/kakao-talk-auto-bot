package com.example.kakaotalkautobot

object LogCopyPolicy {
    data class DialogText(
        val title: String,
        val message: String,
        val positiveButton: String
    )

    fun dialogText(redacted: Boolean): DialogText {
        return if (redacted) {
            DialogText(
                title = "마스킹 로그 복사",
                message = "방 이름, 발화자, 메시지 내용을 가린 로그를 복사합니다.",
                positiveButton = "복사"
            )
        } else {
            DialogText(
                title = "원본 로그 복사",
                message = "원본 로그에는 방 이름, 발화자, 메시지 내용이 그대로 포함될 수 있습니다. 외부 공유 전 민감한 대화가 들어 있는지 다시 확인하세요.",
                positiveButton = "위험을 알고 원본 복사"
            )
        }
    }
}
