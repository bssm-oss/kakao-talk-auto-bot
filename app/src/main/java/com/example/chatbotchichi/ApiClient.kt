package com.example.chatbotchichi

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val BASE_URL = "https://n8n.onthelook.ai/webhook/api/kakao-chat-assistant"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    data class Device(val id: Long, val name: String)

    fun fetchDevices(callback: (List<Device>?, String?) -> Unit) {
        val request = Request.Builder()
            .url("$BASE_URL/devices")
            .get()
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                callback(null, e.message)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!response.isSuccessful) {
                        callback(null, "HTTP ${response.code}")
                        return
                    }
                    val body = response.body?.string().orEmpty()
                    try {
                        val arr = JSONArray(body)
                        val devices = mutableListOf<Device>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            val id = obj.optLong("id", 0L)
                            val name = obj.optString("name", "")
                            if (name.isNotBlank()) {
                                devices.add(Device(id, name))
                            }
                        }
                        callback(devices, null)
                    } catch (e: Exception) {
                        callback(null, e.message)
                    }
                }
            }
        })
    }

    fun registerDevice(deviceName: String, callback: (Device?, String?) -> Unit) {
        val json = JSONObject().put("device_name", deviceName)
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("$BASE_URL/devices")
            .post(body)
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                callback(null, e.message)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!response.isSuccessful) {
                        callback(null, "HTTP ${response.code}")
                        return
                    }
                    val bodyStr = response.body?.string().orEmpty()
                    // Try direct parse first
                    val device = parseDevice(bodyStr, deviceName)
                    if (device != null) {
                        callback(device, null)
                        return
                    }
                    // Fallback: fetch list and find by name
                    fetchDevices { devices, error ->
                        if (devices != null) {
                            val found = devices.firstOrNull { it.name == deviceName }
                            if (found != null) {
                                callback(found, null)
                            } else {
                                callback(Device(0L, deviceName), null)
                            }
                        } else {
                            callback(Device(0L, deviceName), error)
                        }
                    }
                }
            }
        })
    }

    fun postSession(roomName: String, deviceName: String) {
        val json = JSONObject()
            .put("room_name", roomName)
            .put("device_name", deviceName)
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("$BASE_URL/sessions")
            .post(body)
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {}
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.close()
            }
        })
    }

    fun postLog(status: String, message: String, roomName: String, speaker: String, deviceName: String) {
        val json = JSONObject()
            .put("status", status)
            .put("message", message)
            .put("room_name", roomName)
            .put("speaker", speaker)
            .put("device_name", deviceName)
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("$BASE_URL/logs")
            .post(body)
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {}
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.close()
            }
        })
    }

    fun buildInboundUrl(deviceName: String): String {
        val base = "$BASE_URL/messages/inbound"
        val httpUrl = base.toHttpUrl()
            .newBuilder()
            .addQueryParameter("device_name", deviceName)
            .build()
        return httpUrl.toString()
    }

    private fun parseDevice(body: String, expectedName: String): Device? {
        return try {
            val obj = JSONObject(body)
            val id = obj.optLong("id", 0L)
            val name = obj.optString("name", expectedName)
            if (name.isBlank()) null else Device(id, name)
        } catch (_: Exception) {
            try {
                val arr = JSONArray(body)
                val obj = arr.optJSONObject(0) ?: return null
                val id = obj.optLong("id", 0L)
                val name = obj.optString("name", expectedName)
                if (name.isBlank()) null else Device(id, name)
            } catch (_: Exception) {
                null
            }
        }
    }
}
