package com.example.kakaotalkautobot

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.app.RemoteInput

class SessionReplier(
    val context: Context,
    val room: String, // 세션 조회를 위한 키 (방 이름)
    val isDebug: Boolean = false // 디버그 모드 여부
) {
    data class SendResult(
        val sent: Boolean,
        val reason: String? = null
    )

    companion object {
        const val REASON_NO_SESSION = "no session"
        const val REASON_NO_REMOTE_INPUT = "no remoteInput"
        const val REASON_PENDING_INTENT_NULL = "pendingIntent null"
        const val REASON_PENDING_INTENT_SEND_FAILED = "pendingIntent send failed"
        const val REASON_EXCEPTION = "reply exception"
    }

    private val TAG = "BotEngine-Replier"
    private fun logOutgoing(targetRoom: String, message: String, success: Boolean, reason: String? = null) {
        val label = if (success) "OUT" else "OUT_FAIL"
        val base = "[$targetRoom] $message"
        val serverMessage = if (success) {
            message
        } else {
            val detail = if (reason.isNullOrBlank()) "" else " (reason=$reason)"
            "$message$detail"
        }
        val line = if (success) {
            base
        } else {
            val detail = if (reason.isNullOrBlank()) "" else " (reason=$reason)"
            "❌ $base$detail"
        }
        UiLogger.log(
            context,
            label,
            line,
            roomName = targetRoom,
            speaker = "시스템",
            serverMessage = serverMessage,
            eventReason = reason ?: if (success) "sent" else "unknown"
        )
    }

    fun reply(message: String): Boolean {
        return replyToRoomDetailed(this.room, message).sent
    }

    /**
     * 특정 방으로 메시지 전송 (스크립트에서 사용)
     */
    fun replyToRoom(targetRoom: String, message: String): Boolean {
        return replyToRoomDetailed(targetRoom, message).sent
    }

    fun replyToRoomDetailed(targetRoom: String, message: String): SendResult {
        // 1. 세션 매니저에서 캐싱된 실전 Action 조회 (우선 순위 높음)
        val session = SessionManager.getSession(targetRoom)
        
        if (session != null) {
            try {
                val action = session.action
                val remoteInputs = action.remoteInputs
                val pi = action.actionIntent
                if (pi != null) {
                    val line = "pendingIntent=$pi creatorPackage=${pi.creatorPackage} uid=${pi.creatorUid}"
                    Log.d(TAG, line)
                } else {
                    Log.d(TAG, "pendingIntent=null")
                }
                
                if (!remoteInputs.isNullOrEmpty()) {
                    val remoteInput = remoteInputs.first()
                    val intent = Intent()
                    val bundle = Bundle()
                    bundle.putCharSequence(remoteInput.resultKey, message)
                    RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
                    val clip = intent.clipData
                    if (clip != null) {
                        val clipLine = "clipData items=${clip.itemCount} label=${clip.description?.label}"
                        Log.d(TAG, clipLine)
                    } else {
                        Log.d(TAG, "clipData=null")
                    }

                    val pendingIntent = action.actionIntent
                    if (pendingIntent == null) {
                        Log.e(TAG, "Reply failed: PendingIntent is null for $targetRoom")
                        logOutgoing(targetRoom, message, false, REASON_PENDING_INTENT_NULL)
                        return SendResult(false, REASON_PENDING_INTENT_NULL)
                    }
                    try {
                        pendingIntent.send(context, 0, intent)
                    } catch (e: Throwable) {
                        Log.e(TAG, "Reply failed: PendingIntent send failed for $targetRoom", e)
                        logOutgoing(targetRoom, message, false, REASON_PENDING_INTENT_SEND_FAILED)
                        return SendResult(false, REASON_PENDING_INTENT_SEND_FAILED)
                    }
                    Log.d(TAG, "Reply sent to $targetRoom: $message")
                    RoomStore.recordOutgoing(context, targetRoom, message)
                    logOutgoing(targetRoom, message, true, null)

                    // 디버그 모드에서 실전송 성공 시, 디버깅 룸에도 로그 남김
                    if (isDebug) {
                        val intentDebug = Intent("com.example.kakaotalkautobot.BOT_REPLY")
                        intentDebug.putExtra("msg", "✅ [실전송] $targetRoom: $message")
                        intentDebug.putExtra("room", room) // 현재 디버깅 중인 방에 표시
                        intentDebug.setPackage(context.packageName)
                        context.sendBroadcast(intentDebug)
                    }
                    return SendResult(true)
                }
                logOutgoing(targetRoom, message, false, REASON_NO_REMOTE_INPUT)
                return SendResult(false, REASON_NO_REMOTE_INPUT)
            } catch (e: Throwable) {
                Log.e(TAG, "Reply failed", e)
                logOutgoing(targetRoom, message, false, REASON_EXCEPTION)
                return SendResult(false, REASON_EXCEPTION)
            }
        }

        // 2. 실전 세션이 없고 디버그 모드인 경우 (가상 시뮬레이션)
        if (isDebug) {
            Log.d(TAG, "[DEBUG] Simulation Reply to $targetRoom: $message")
            RoomStore.recordOutgoing(context, targetRoom, message)
            val intent = Intent("com.example.kakaotalkautobot.BOT_REPLY")
            intent.putExtra("msg", "🛠 [가상] $targetRoom: $message")
            intent.putExtra("room", room)
            intent.setPackage(context.packageName)
            context.sendBroadcast(intent)
            return SendResult(true)
        }

        Log.e(TAG, "Reply failed: No session found for room $targetRoom")
        logOutgoing(targetRoom, message, false, REASON_NO_SESSION)
        return SendResult(false, REASON_NO_SESSION)
    }

    fun log(message: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val line = "[$time] $message"
        LogStore.append(context, line)
        val intent = Intent("com.example.kakaotalkautobot.LOG_UPDATE")
        intent.putExtra("log", line)
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)
    }
}
