package com.phoneagent.providers

import com.phoneagent.agent.AgentRequest
import com.phoneagent.agent.AgentResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAiCompatibleProvider(
    override val config: ProviderConfig
) : AiProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun chatCompletion(request: AgentRequest): AgentResponse = withContext(Dispatchers.IO) {
        val body = buildRequestBody(request, stream = false)
        val httpRequest = buildHttpRequest(body)

        try {
            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw mapHttpError(response.code, response.body?.string() ?: "Unknown error")
                }
                val responseBody = response.body?.string() ?: throw ProviderError.UnknownError("Empty response")
                parseResponse(responseBody)
            }
        } catch (e: IOException) {
            throw ProviderError.NetworkError(e.message ?: "Network error")
        }
    }

    override fun chatCompletionStream(request: AgentRequest): Flow<String> = flow {
        val body = buildRequestBody(request, stream = true)
        val httpRequest = buildHttpRequest(body)

        try {
            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw mapHttpError(response.code, response.body?.string() ?: "Unknown error")
                }
                val source = response.body?.source() ?: throw ProviderError.UnknownError("Empty response")
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("data: ")) {
                        val data = line.substring(6)
                        if (data == "[DONE]") break
                        val content = StreamingParser.extractContentFromChunk(data)
                        if (content != null) {
                            emit(content)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            throw ProviderError.NetworkError(e.message ?: "Network error")
        }
    }

    override fun isAvailable(): Boolean {
        return config.baseUrl.isNotBlank()
    }

    private fun buildRequestBody(request: AgentRequest, stream: Boolean): String {
        val messages = JSONArray()
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", "You are PhoneAgent, a concise Android-native assistant running from the user's phone.")
        })
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", request.message)
        })

        return JSONObject().apply {
            put("model", request.model)
            put("messages", messages)
            put("temperature", 0.7)
            put("stream", stream)
        }.toString()
    }

    private fun buildHttpRequest(body: String): Request {
        val url = "${config.baseUrl.trimEnd('/')}/chat/completions"
        val builder = Request.Builder()
            .url(url)
            .post(body.toRequestBody(jsonMediaType))
            .header("Content-Type", "application/json")

        config.apiKey?.let {
            if (it.isNotBlank()) {
                builder.header("Authorization", "Bearer $it")
            }
        }

        return builder.build()
    }

    private fun parseResponse(jsonString: String): AgentResponse {
        val json = JSONObject(jsonString)
        val choices = json.getJSONArray("choices")
        val message = choices.getJSONObject(0).getJSONObject("message")
        val content = message.getString("content")
        val model = json.optString("model", "unknown")
        return AgentResponse(
            content = content,
            model = model,
            usage = json.optJSONObject("usage")?.toString()
        )
    }

    private fun mapHttpError(code: Int, body: String): ProviderError {
        return when (code) {
            401 -> ProviderError.AuthenticationError("Invalid API key")
            429 -> ProviderError.RateLimitError("Rate limit exceeded")
            400, 422 -> ProviderError.InvalidRequestError(body)
            in 500..599 -> ProviderError.ServerError("Server error: $code")
            else -> ProviderError.UnknownError("HTTP $code: $body")
        }
    }
}
