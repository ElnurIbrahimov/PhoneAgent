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

abstract class BaseOpenAiProvider : AiProvider {

    abstract override val config: ProviderConfig

    protected open val readTimeoutSeconds: Long = 60L

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun chatCompletion(request: AgentRequest): AgentResponse = withContext(Dispatchers.IO) {
        val body = buildRequestBody(request, stream = false)
        val httpRequest = buildHttpRequest(body)

        try {
            client.newCall(httpRequest).execute().use { response ->
                val bodyString = response.body?.string() ?: throw ProviderError.UnknownError("Empty response")
                if (!response.isSuccessful) {
                    throw mapHttpError(response.code, bodyString)
                }
                parseResponse(bodyString)
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

    override suspend fun chatCompletionStream(
        request: AgentRequest,
        onChunk: (com.phoneagent.streaming.StreamChunk) -> Unit
    ) = withContext(Dispatchers.IO) {
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
                    val chunk = com.phoneagent.streaming.StreamChunk(
                        type = com.phoneagent.streaming.ChunkType.TOKEN,
                        content = line
                    )
                    onChunk(chunk)
                }
            }
        } catch (e: IOException) {
            throw ProviderError.NetworkError(e.message ?: "Network error")
        }
    }

    private suspend fun simulateStreaming(
        content: String,
        onChunk: (com.phoneagent.streaming.StreamChunk) -> Unit
    ) = withContext(Dispatchers.IO) {
        val words = content.split(" ")
        for (word in words) {
            onChunk(com.phoneagent.streaming.StreamChunk(
                com.phoneagent.streaming.ChunkType.TOKEN,
                "$word "
            ))
            kotlinx.coroutines.delay(20)
        }
        onChunk(com.phoneagent.streaming.StreamChunk(com.phoneagent.streaming.ChunkType.DONE, ""))
    }

    override fun isAvailable(): Boolean {
        return config.baseUrl.isNotBlank()
    }

    protected open fun buildRequestBody(request: AgentRequest, stream: Boolean): String {
        val messages = JSONArray()
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", request.systemPrompt)
        })

        if (request.visionPayload != null) {
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "text")
                        put("text", request.message)
                    })
                    val visionArr = JSONArray(request.visionPayload)
                    for (i in 0 until visionArr.length()) {
                        put(visionArr.get(i))
                    }
                })
            })
        } else {
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", request.message)
            })
        }

        return JSONObject().apply {
            put("model", request.model)
            put("messages", messages)
            put("temperature", request.temperature)
            put("stream", stream)
        }.toString()
    }

    protected open fun buildHttpRequest(body: String): Request {
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

    protected open fun parseResponse(jsonString: String): AgentResponse {
        val json = JSONObject(jsonString)
        val choices = json.optJSONArray("choices")
            ?: throw ProviderError.UnknownError("Invalid response: missing 'choices' array")
        if (choices.length() == 0) throw ProviderError.UnknownError("Empty response: no choices returned")
        val message = choices.getJSONObject(0).optJSONObject("message")
            ?: throw ProviderError.UnknownError("Invalid response: missing 'message' object")
        val content = message.optString("content", "")
        val finishReason = choices.getJSONObject(0).optString("finish_reason", "stop")
        if (finishReason == "content_filter") {
            throw ProviderError.InvalidRequestError("Content filtered by provider safety system.")
        }
        val model = json.optString("model", "unknown")
        return AgentResponse(
            content = content,
            model = model,
            finishReason = finishReason
        )
    }

    protected open fun mapHttpError(code: Int, body: String): ProviderError {
        return when (code) {
            401 -> ProviderError.AuthenticationError("Invalid API key")
            403 -> ProviderError.AuthenticationError("Access denied. Check your API subscription.")
            404 -> ProviderError.InvalidRequestError("Endpoint not found. Check your base URL and model name.")
            408 -> ProviderError.NetworkError("Request timed out. The server took too long to respond.")
            429 -> ProviderError.RateLimitError("Rate limit exceeded")
            400, 422 -> ProviderError.InvalidRequestError(body)
            in 500..599 -> ProviderError.ServerError("Server error: $code")
            else -> ProviderError.UnknownError("HTTP $code: $body")
        }
    }
}
