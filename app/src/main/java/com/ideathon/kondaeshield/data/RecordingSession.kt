package com.ideathon.kondaeshield.data

import org.json.JSONObject

data class RecordingSession(
    val id: String,
    val createdAtEpochMillis: Long,
    val audioFilePath: String,
    val state: ProcessingState,
    val transcript: String = "",
    val summary: String = "",
    val businessSummary: String = "",
    val comfortInterpretation: String = "",
    val naggingAnalysis: String = "",
    val errorMessage: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("createdAtEpochMillis", createdAtEpochMillis)
        .put("audioFilePath", audioFilePath)
        .put("state", state.name)
        .put("transcript", transcript)
        .put("summary", summary)
        .put("businessSummary", businessSummary)
        .put("comfortInterpretation", comfortInterpretation)
        .put("naggingAnalysis", naggingAnalysis)
        .put("errorMessage", errorMessage)

    companion object {
        fun fromJson(json: JSONObject): RecordingSession = RecordingSession(
            id = json.getString("id"),
            createdAtEpochMillis = json.getLong("createdAtEpochMillis"),
            audioFilePath = json.getString("audioFilePath"),
            state = ProcessingState.valueOf(json.getString("state")),
            transcript = json.optString("transcript", ""),
            summary = json.optString("summary", ""),
            businessSummary = json.optString("businessSummary", json.optString("summary", "")),
            comfortInterpretation = json.optString("comfortInterpretation", ""),
            naggingAnalysis = json.optString("naggingAnalysis", ""),
            errorMessage = json.optString("errorMessage").ifBlank { null },
        )
    }
}
