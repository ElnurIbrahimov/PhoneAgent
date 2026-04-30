package com.phoneagent.kira

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class KiraBridge(
    private val baseUrl: String = "http://127.0.0.1:7071"
) {
    private val client = OkHttpClient.Builder()
        .callTimeout(3, TimeUnit.SECONDS)
        .connectTimeout(1, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            withTimeout(2000) {
                val req = Request.Builder().url("$baseUrl/pa/health").get().build()
                val res = client.newCall(req).execute()
                res.isSuccessful
            }
        } catch (_: Exception) { false }
    }

    suspend fun getContext(userMessage: String): KiraContext = withContext(Dispatchers.IO) {
        try {
            withTimeout(2000) {
                val encoded = java.net.URLEncoder.encode(userMessage.take(400), "UTF-8")
                val req = Request.Builder().url("$baseUrl/pa/context?msg=$encoded").get().build()
                val body = client.newCall(req).execute().use { it.body?.string() ?: """{"context":""}""" }
                parseContext(body)
            }
        } catch (_: Exception) { KiraContext() }
    }

    suspend fun getIrisRouting(message: String): KiraIrisRouting = withContext(Dispatchers.IO) {
        try {
            withTimeout(2000) {
                val encoded = java.net.URLEncoder.encode(message.take(400), "UTF-8")
                val req = Request.Builder().url("$baseUrl/pa/iris?msg=$encoded").get().build()
                val body = client.newCall(req).execute().use { it.body?.string() ?: """{}""" }
                parseIris(body)
            }
        } catch (_: Exception) { KiraIrisRouting() }
    }

    suspend fun postSessionDone(messages: List<Pair<String, String>>) = withContext(Dispatchers.IO) {
        try {
            withTimeout(3000) {
                val arr = JSONArray()
                messages.forEach { (role, content) ->
                    arr.put(JSONObject().apply {
                        put("role", role)
                        put("content", content.take(2000))
                    })
                }
                val json = JSONObject().apply { put("messages", arr) }
                val body = json.toString().toRequestBody(JSON)
                val req = Request.Builder().url("$baseUrl/pa/session-done").post(body).build()
                client.newCall(req).execute().close()
            }
        } catch (_: Exception) {}
    }

    suspend fun postObservation(
        appName: String? = null,
        packageName: String? = null,
        activity: String? = null,
        context: String? = null,
        ocrText: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            withTimeout(2000) {
                val json = JSONObject()
                appName?.let { json.put("app", it) }
                packageName?.let { json.put("package", it) }
                activity?.let { json.put("activity", it) }
                context?.let { json.put("context", it) }
                ocrText?.let { json.put("ocrText", it.take(1000)) }
                val body = json.toString().toRequestBody(JSON)
                val req = Request.Builder().url("$baseUrl/pa/observe").post(body).build()
                client.newCall(req).execute().close()
            }
        } catch (_: Exception) {}
    }

    suspend fun updateEmotion(tension: Double? = null, connection: Double? = null,
                              energy: Double? = null, focus: Double? = null) = withContext(Dispatchers.IO) {
        try {
            withTimeout(2000) {
                val json = JSONObject()
                tension?.let { json.put("tension", it) }
                connection?.let { json.put("connection", it) }
                energy?.let { json.put("energy", it) }
                focus?.let { json.put("focus", it) }
                val body = json.toString().toRequestBody(JSON)
                val req = Request.Builder().url("$baseUrl/pa/emotion").post(body).build()
                client.newCall(req).execute().close()
            }
        } catch (_: Exception) {}
    }

    suspend fun getHealth(): KiraHealth = withContext(Dispatchers.IO) {
        try {
            withTimeout(2000) {
                val req = Request.Builder().url("$baseUrl/pa/health").get().build()
                val body = client.newCall(req).execute().use { it.body?.string() ?: """{}""" }
                parseHealth(body)
            }
        } catch (_: Exception) { KiraHealth() }
    }

    private fun parseContext(json: String): KiraContext {
        return try {
            val obj = JSONObject(json)
            KiraContext(context = obj.optString("context", ""))
        } catch (_: Exception) { KiraContext() }
    }

    private fun parseIris(json: String): KiraIrisRouting {
        return try {
            val obj = JSONObject(json)
            KiraIrisRouting(
                profile = obj.optString("profile", "balanced"),
                temperature = obj.optDouble("temperature", 0.5),
                maxTokens = obj.optInt("maxTokens", 2048),
                style = obj.optString("style", "clear and complete"),
                depth = obj.optString("depth", "medium"),
                warmthBoost = obj.optBoolean("warmthBoost", false),
                styleInjection = obj.optString("styleInjection", ""),
                reasoning = obj.optString("reasoning", "baseline")
            )
        } catch (_: Exception) { KiraIrisRouting() }
    }

    private fun parseHealth(json: String): KiraHealth {
        return try {
            val obj = JSONObject(json)
            KiraHealth(
                status = obj.optString("status", ""),
                mood = obj.optString("mood", "neutral"),
                iris = obj.optString("iris", "")
            )
        } catch (_: Exception) { KiraHealth() }
    }
}
