package com.example.chatbotchichi

import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

object ApiClient {
    private const val BASE_URL = "https://YOUR_N8N_URL/webhook/api/kakao-chat-assistant"
    private const val TAG = "BotEngine-ApiClient"
    private val client = SharedHttpClient.instance

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
        val txBytes = estimateRequestSizeBytes(request)
        Log.d(TAG, "postLog 요청 시작: tx~${txBytes}B status=$status room=$roomName")
        val usageBefore = NetworkUsageMeter.snapshot()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e(TAG, "postLog 실패: ${e.message}")
                NetworkUsageMeter.logDelta(TAG, "postLog 실측", usageBefore, NetworkUsageMeter.snapshot())
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    val rxBytes = response.body?.bytes()?.size ?: 0
                    Log.d(TAG, "postLog 응답 수신: code=${response.code}, rx=${rxBytes}B")
                }
                NetworkUsageMeter.logDelta(TAG, "postLog 실측", usageBefore, NetworkUsageMeter.snapshot())
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

    private fun estimateRequestSizeBytes(request: Request): Long {
        var total = 0L
        val encodedPath = request.url.encodedPath
        val encodedQuery = request.url.encodedQuery
        val target = if (encodedQuery.isNullOrBlank()) encodedPath else "$encodedPath?$encodedQuery"
        total += "${request.method} $target HTTP/1.1\r\n".toByteArray(Charsets.UTF_8).size
        for (i in 0 until request.headers.size) {
            val name = request.headers.name(i)
            val value = request.headers.value(i)
            total += "$name: $value\r\n".toByteArray(Charsets.UTF_8).size
        }
        total += 2
        val bodyLength = request.body?.contentLength()?.takeIf { it > 0L } ?: 0L
        return total + bodyLength
    }
}
