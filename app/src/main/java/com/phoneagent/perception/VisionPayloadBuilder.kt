package com.phoneagent.perception

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

class VisionPayloadBuilder {

    fun buildVisionPayload(imageBase64: String, text: String): String {
        val contentArray = JSONArray()
        contentArray.put(JSONObject().apply {
            put("type", "text")
            put("text", text)
        })
        contentArray.put(JSONObject().apply {
            put("type", "image_url")
            put("image_url", JSONObject().apply {
                put("url", "data:image/jpeg;base64,$imageBase64")
                put("detail", "high")
            })
        })
        return contentArray.toString()
    }

    fun buildVisionContextPayload(imageBase64: String, context: String): String {
        val contentArray = JSONArray()
        contentArray.put(JSONObject().apply {
            put("type", "text")
            put("text", "Current phone screen. Use this to understand the UI state. $context")
        })
        contentArray.put(JSONObject().apply {
            put("type", "image_url")
            put("image_url", JSONObject().apply {
                put("url", "data:image/jpeg;base64,$imageBase64")
                put("detail", "high")
            })
        })
        return contentArray.toString()
    }

    fun buildVisionSystemPrompt(baseSystemPrompt: String): String {
        return "$baseSystemPrompt\n\nYou can see a screenshot of the phone screen with each request. " +
               "Use the visual information alongside the accessibility tree to navigate and interact with the UI. " +
               "When tapping, prefer accessibility.tap_text over coordinates when possible."
    }

    companion object {
        fun encodeImage(bytes: ByteArray): String {
            return Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }
}
