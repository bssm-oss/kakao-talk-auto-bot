package com.example.chatbotchichi

import android.app.Notification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput

/**
 * 채팅방별 답장 세션(Action)을 캐싱하여 관리하는 싱글톤
 * 한 번 알림이 오면 해당 방의 세션을 저장해두고, 이후 계속 답장할 수 있게 함.
 */
object SessionManager {
    private val TAG = "BotEngine-Session"
    
    // 방 이름 -> (Action, 패키지명) 맵
    private val sessionMap = mutableMapOf<String, CachedSession>()

    data class CachedSession(
        val action: NotificationCompat.Action,
        val packageName: String
    )

    /**
     * 알림에서 답장 가능한 Action을 추출하여 세션으로 저장
     */
    fun bindSession(room: String, notification: Notification, packageName: String) {
        // 1. WearableExtender에서 찾기 (카카오톡 등 대부분 여기 있음)
        val wearableExtender = NotificationCompat.WearableExtender(notification)
        for (action in wearableExtender.actions) {
            if (isValidRemoteInput(action)) {
                save(room, action, packageName)
                return
            }
        }

        // 2. 일반 Action에서 찾기 (일부 앱 호환성)
        val count = NotificationCompat.getActionCount(notification)
        for (i in 0 until count) {
            val action = NotificationCompat.getAction(notification, i)
            if (action != null && isValidRemoteInput(action)) {
                save(room, action, packageName)
                return
            }
        }
    }

    private fun isValidRemoteInput(action: NotificationCompat.Action): Boolean {
        val remoteInputs = action.remoteInputs ?: return false
        return remoteInputs.isNotEmpty()
    }

    private fun save(room: String, action: NotificationCompat.Action, packageName: String) {
        // 최신 세션으로 갱신
        sessionMap[room] = CachedSession(action, packageName)
        Log.d(TAG, "세션 갱신 완료: $room")
    }

    fun getSession(room: String): CachedSession? {
        return sessionMap[room]
    }
}
