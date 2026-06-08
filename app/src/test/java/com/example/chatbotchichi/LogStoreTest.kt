package com.example.kakaotalkautobot

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogStoreTest {
    @Test
    fun pruneIfExpired_deletesLogsOlderThanRetentionWindow() {
        val file = File.createTempFile("old-log", ".log")
        val nowMs = 10L * 24L * 60L * 60L * 1000L
        file.writeText("[12:00:00][IN] [친구방] 민수: 오래된 메시지")
        file.setLastModified(0L)

        val deleted = LogStore.pruneIfExpired(
            file = file,
            nowMs = nowMs,
            retentionDays = LogStore.DEFAULT_RETENTION_DAYS
        )

        assertTrue(deleted)
        assertFalse(file.exists())
    }

    @Test
    fun pruneIfExpired_keepsLogsWithinRetentionWindow() {
        val file = File.createTempFile("fresh-log", ".log")
        val dayMs = 24L * 60L * 60L * 1000L
        val nowMs = 10L * dayMs
        file.writeText("[12:00:00][IN] [친구방] 민수: 최근 메시지")
        file.setLastModified(nowMs - 2L * dayMs)

        val deleted = LogStore.pruneIfExpired(
            file = file,
            nowMs = nowMs,
            retentionDays = LogStore.DEFAULT_RETENTION_DAYS
        )

        assertFalse(deleted)
        assertTrue(file.exists())
        file.delete()
    }
}
