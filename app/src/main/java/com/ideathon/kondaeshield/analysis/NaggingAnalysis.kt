package com.ideathon.kondaeshield.analysis

import org.json.JSONObject

data class NaggingAnalysis(
    val typeCode: String,
    val typeName: String,
    val totalPercent: Int,
    val blamePercent: Int,
    val comparisonPercent: Int,
    val commandPercent: Int,
    val lattePercent: Int,
    val practicalAdvicePercent: Int,
    val explanation: String,
    val rewriteSuggestion: String,
) {
    val resultLabel: String
        get() = "${typeCode}형 · $typeName"

    fun toJsonString(): String = JSONObject()
        .put("schemaVersion", SCHEMA_VERSION)
        .put("typeCode", typeCode)
        .put("typeName", typeName)
        .put("totalPercent", totalPercent)
        .put("blamePercent", blamePercent)
        .put("comparisonPercent", comparisonPercent)
        .put("commandPercent", commandPercent)
        .put("lattePercent", lattePercent)
        .put("practicalAdvicePercent", practicalAdvicePercent)
        .put("explanation", explanation)
        .put("rewriteSuggestion", rewriteSuggestion)
        .toString()

    companion object {
        private const val SCHEMA_VERSION = 1

        fun fromJsonString(value: String): NaggingAnalysis? =
            runCatching {
                val json = JSONObject(value)
                NaggingAnalysis(
                    typeCode = json.getString("typeCode"),
                    typeName = json.getString("typeName"),
                    totalPercent = json.getInt("totalPercent"),
                    blamePercent = json.getInt("blamePercent"),
                    comparisonPercent = json.getInt("comparisonPercent"),
                    commandPercent = json.getInt("commandPercent"),
                    lattePercent = json.getInt("lattePercent"),
                    practicalAdvicePercent = json.getInt("practicalAdvicePercent"),
                    explanation = json.getString("explanation"),
                    rewriteSuggestion = json.getString("rewriteSuggestion"),
                )
            }.getOrNull()
    }
}
