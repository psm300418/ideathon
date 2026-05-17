package com.ideathon.kondaeshield.settings

enum class ApiProvider(
    val displayName: String,
    val supportsTranscription: Boolean,
) {
    OPENAI("OpenAI", true),
    GROQ("Groq", true),
}
