package com.phoneagent.soma.daemon

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object TelegramPush {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun push(botToken: String, chatId: String, message: String): Boolean {
        return try {
            val url = "https://api.telegram.org/bot$botToken/sendMessage"
            val body = JSONObject().apply {
                put("chat_id", chatId)
                put("text", "Kira: $message")
                put("parse_mode", "HTML")
            }.toString()

            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }
}
