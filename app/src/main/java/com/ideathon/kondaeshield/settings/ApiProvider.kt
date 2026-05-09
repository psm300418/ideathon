package com.ideathon.kondaeshield.settings

enum class ApiProvider(
    val displayName: String,
    val supportsTranscription: Boolean,
) {
    GROQ("Groq", true),
    OPENAI("OpenAI", true),
    GEMINI("Gemini", false),
    CLAUDE("Claude", false),
    CUSTOM("기타", false),
}
