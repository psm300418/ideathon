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
    val empathyMessage: String,
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
        .put("empathyMessage", empathyMessage)
        .toString()

    companion object {
        private const val SCHEMA_VERSION = 2

        fun fromJsonString(value: String): NaggingAnalysis? =
            runCatching {
                val json = JSONObject(value)
                val typeCode = json.getString("typeCode")
                val typeName = json.getString("typeName")
                NaggingAnalysis(
                    typeCode = typeCode,
                    typeName = typeName,
                    totalPercent = json.getInt("totalPercent"),
                    blamePercent = json.getInt("blamePercent"),
                    comparisonPercent = json.getInt("comparisonPercent"),
                    commandPercent = json.getInt("commandPercent"),
                    lattePercent = json.getInt("lattePercent"),
                    practicalAdvicePercent = json.getInt("practicalAdvicePercent"),
                    explanation = json.getString("explanation"),
                    empathyMessage = json.optString("empathyMessage")
                        .ifBlank { defaultEmpathyMessage(typeCode, typeName) },
                )
            }.getOrNull()

        private fun defaultEmpathyMessage(typeCode: String, typeName: String): String =
            "이 기록은 ${typeCode}형 · $typeName 쪽에 가까워 보여요. 이런 말을 듣고 마음이 무거웠다면 이상한 게 아닙니다. " +
                "사용자는 단순히 예민한 게 아니라, 존중보다 압박이 앞서는 잔소리를 버틴 쪽에 가깝습니다."
    }
}
