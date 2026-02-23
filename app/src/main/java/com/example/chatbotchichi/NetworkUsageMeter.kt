package com.example.chatbotchichi

import android.net.TrafficStats
import android.os.Process
import android.util.Log

object NetworkUsageMeter {
    private val uid: Int = Process.myUid()

    data class Snapshot(
        val txBytes: Long,
        val rxBytes: Long
    )

    fun snapshot(): Snapshot? {
        val tx = TrafficStats.getUidTxBytes(uid)
        val rx = TrafficStats.getUidRxBytes(uid)
        if (tx < 0L || rx < 0L) return null
        return Snapshot(tx, rx)
    }

    fun logDelta(tag: String, label: String, before: Snapshot?, after: Snapshot?) {
        if (before == null || after == null) {
            Log.d(tag, "$label uidΔ 측정 불가")
            return
        }
        val tx = (after.txBytes - before.txBytes).coerceAtLeast(0L)
        val rx = (after.rxBytes - before.rxBytes).coerceAtLeast(0L)
        val total = tx + rx
        Log.d(tag, "$label uidΔ tx=${tx}B rx=${rx}B total=${total}B")
    }
}
