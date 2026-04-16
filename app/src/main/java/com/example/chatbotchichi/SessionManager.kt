package com.example.kakaotalkautobot

import android.app.Notification
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import org.json.JSONObject

/**
 * 채팅방별 답장 세션(Action)을 캐싱하여 관리하는 싱글톤
 * 한 번 알림이 오면 해당 방의 세션을 저장해두고, 이후 계속 답장할 수 있게 함.
 */
object SessionManager {
    private val TAG = "BotEngine-Session"
    private const val PREFS_NAME = "SessionPrefs"
    private const val KEY_ROOM_TIMES = "room_times"
    
    // 방 이름 -> (Action, 패키지명) 맵
    private val sessionMap = mutableMapOf<String, CachedSession>()
    private val roomLastSeen = mutableMapOf<String, Long>()
    private var roomsLoaded = false
    data class CachedSession(
        val action: NotificationCompat.Action,
        val packageName: String
    )

    /**
     * 알림에서 답장 가능한 Action을 추출하여 세션으로 저장
     */
    fun bindSession(context: Context, room: String, notification: Notification, packageName: String) {
        // 1. WearableExtender에서 찾기 (카카오톡 등 대부분 여기 있음)
        val wearableExtender = NotificationCompat.WearableExtender(notification)
        for (action in wearableExtender.actions) {
            if (isValidRemoteInput(action)) {
                save(context, room, action, packageName)
                return
            }
        }

        // 2. 일반 Action에서 찾기 (일부 앱 호환성)
        val count = NotificationCompat.getActionCount(notification)
        for (i in 0 until count) {
            val action = NotificationCompat.getAction(notification, i)
            if (action != null && isValidRemoteInput(action)) {
                save(context, room, action, packageName)
                return
            }
        }
    }

    private fun isValidRemoteInput(action: NotificationCompat.Action): Boolean {
        val remoteInputs = action.remoteInputs ?: return false
        return remoteInputs.isNotEmpty()
    }

    private fun save(context: Context, room: String, action: NotificationCompat.Action, packageName: String) {
        loadRooms(context)
        // 최신 세션으로 갱신
        sessionMap[room] = CachedSession(action, packageName)
        touchRoom(context, room)
        val header = "세션 갱신 완료: room=$room pkg=$packageName actionTitle=${action.title} icon=${action.icon}"
        Log.d(TAG, header)
        val ris = action.remoteInputs
        if (!ris.isNullOrEmpty()) {
            Log.d(TAG, "세션 RemoteInputs count=${ris.size}")
            ris.forEachIndexed { idx, ri ->
                val line = "RemoteInput[$idx] resultKey=${ri.resultKey} label=${ri.label} allowFreeForm=${ri.allowFreeFormInput} choices=${ri.choices?.size ?: 0} extrasKeys=${ri.extras.keySet()}"
                Log.d(TAG, line)
            }
        } else {
            Log.d(TAG, "세션 RemoteInputs 없음")
        }

    }

    fun getSession(room: String): CachedSession? {
        return sessionMap[room]
    }

    /**
     * 현재 세션이 저장된 방 이름 목록 반환
     */
    data class RoomEntry(
        val name: String,
        val isActive: Boolean,
        val lastSeen: Long
    )

    fun getRegisteredRooms(context: Context): List<RoomEntry> {
        loadRooms(context)
        val allRooms = (roomLastSeen.keys + sessionMap.keys).distinct().sorted()
        return allRooms.map { room ->
            RoomEntry(
                name = room,
                isActive = sessionMap.containsKey(room),
                lastSeen = roomLastSeen[room] ?: 0L
            )
        }
    }

    private fun touchRoom(context: Context, room: String) {
        if (room.isBlank()) return
        roomLastSeen[room] = System.currentTimeMillis()
        persistRooms(context)
    }

    private fun loadRooms(context: Context) {
        if (roomsLoaded) return
        roomsLoaded = true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_ROOM_TIMES, null) ?: return
        try {
            val json = JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                roomLastSeen[key] = json.optLong(key, 0L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "세션 목록 로드 실패", e)
        }
    }

    private fun persistRooms(context: Context) {
        try {
            val json = JSONObject()
            for ((room, time) in roomLastSeen) {
                json.put(room, time)
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_ROOM_TIMES, json.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "세션 목록 저장 실패", e)
        }
    }
}
