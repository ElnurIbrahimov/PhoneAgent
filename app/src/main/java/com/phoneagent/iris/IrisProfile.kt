package com.phoneagent.iris

enum class IrisProfile(val description: String, val temperature: Double, val maxTokens: Int) {
    REFLEX("Respond instantly, one line, no explanation. For commands and trivial requests.", 0.2, 128),
    FAST("Direct, 2-3 lines, no filler. For simple questions.", 0.4, 512),
    SHARP("Precise, technical, no filler. For code, debugging, errors.", 0.2, 2048),
    GENTLE("Warm, present, slow. For high-tension or emotional moments.", 0.7, 1024),
    BALANCED("Clear and complete. For everyday conversation.", 0.5, 1024),
    DEEP("Thorough, full depth. For complex reasoning or multi-step tasks.", 0.4, 4096)
}
