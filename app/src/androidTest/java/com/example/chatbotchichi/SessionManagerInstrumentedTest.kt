package com.example.kakaotalkautobot

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionManagerInstrumentedTest {

    @Test
    fun registeredRooms_includePersistedInactiveRooms() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("SessionPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("room_times", "{\"저장된방\":12345}").commit()

        val roomsLoadedField = SessionManager::class.java.getDeclaredField("roomsLoaded")
        roomsLoadedField.isAccessible = true
        roomsLoadedField.setBoolean(null, false)

        val sessionMapField = SessionManager::class.java.getDeclaredField("sessionMap")
        sessionMapField.isAccessible = true
        (sessionMapField.get(null) as MutableMap<*, *>).clear()

        val roomLastSeenField = SessionManager::class.java.getDeclaredField("roomLastSeen")
        roomLastSeenField.isAccessible = true
        (roomLastSeenField.get(null) as MutableMap<*, *>).clear()

        val rooms = SessionManager.getRegisteredRooms(context)

        assertEquals(1, rooms.size)
        assertEquals("저장된방", rooms.first().name)
        assertFalse(rooms.first().isActive)
        assertEquals(12345L, rooms.first().lastSeen)
    }
}
