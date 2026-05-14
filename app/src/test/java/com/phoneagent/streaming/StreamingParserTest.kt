package com.phoneagent.streaming

import com.phoneagent.providers.StreamingParser
import org.junit.Assert.*
import org.junit.Test

class StreamingParserTest {

    @Test
    fun `parseChunk token from SSE data`() {
        val chunk = """data: {"choices":[{"delta":{"content":"Hello"},"finish_reason":null}]}"""
        val result = StreamingParser.parseChunk(chunk, "openai-compatible")
        assertEquals(ChunkType.TOKEN, result.type)
        assertEquals("Hello", result.content)
    }

    @Test
    fun `parseChunk DONE from SSE`() {
        val result = StreamingParser.parseChunk("data: [DONE]", "openai-compatible")
        assertEquals(ChunkType.DONE, result.type)
    }

    @Test
    fun `parseChunk DONE bare`() {
        val result = StreamingParser.parseChunk("[DONE]", "openai-compatible")
        assertEquals(ChunkType.DONE, result.type)
    }

    @Test
    fun `parseChunk reasoning content`() {
        val chunk = """data: {"choices":[{"delta":{"reasoning_content":"let me think"},"finish_reason":null}]}"""
        val result = StreamingParser.parseChunk(chunk, "openai-compatible")
        assertEquals(ChunkType.REASONING, result.type)
        assertEquals("let me think", result.content)
    }

    @Test
    fun `parseChunk tool_call end`() {
        val chunk = """data: {"choices":[{"finish_reason":"tool_calls"}]}"""
        val result = StreamingParser.parseChunk(chunk, "openai-compatible")
        assertEquals(ChunkType.TOOL_CALL_END, result.type)
    }

    @Test
    fun `parseChunk empty delta returns TOKEN`() {
        val chunk = """data: {"choices":[{"delta":{}}]}"""
        val result = StreamingParser.parseChunk(chunk, "openai-compatible")
        assertEquals(ChunkType.TOKEN, result.type)
        assertEquals("", result.content)
    }

    @Test
    fun `parseChunk JSON object without data prefix`() {
        val chunk = """{"choices":[{"delta":{"content":"test"}}]}"""
        val result = StreamingParser.parseChunk(chunk, "openai-compatible")
        assertEquals(ChunkType.TOKEN, result.type)
        assertEquals("test", result.content)
    }

    @Test
    fun `parseChunk raw text returns TOKEN`() {
        val result = StreamingParser.parseChunk("some raw text", "openai-compatible")
        assertEquals(ChunkType.TOKEN, result.type)
        assertEquals("some raw text", result.content)
    }

    @Test
    fun `parseChunk malformed JSON returns TOKEN with raw content`() {
        val result = StreamingParser.parseChunk("not valid json {", "openai-compatible")
        assertEquals(ChunkType.TOKEN, result.type)
    }
}