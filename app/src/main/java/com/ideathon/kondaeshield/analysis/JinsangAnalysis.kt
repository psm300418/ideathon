package com.ideathon.kondaeshield.analysis

import org.json.JSONObject

data class JinsangAnalysis(
    val typeCode: String,
    val typeName: String,
    val totalPercent: Int,
    val blamePercent: Int,
    val belittlingPercent: Int,
    val demandPercent: Int,
    val entitlementPercent: Int,
    val interferencePercent: Int,
    val explanation: String,
    val empathyMessage: String,
    val analyzedAtEpochMillis: Long,
) {
    val resultLabel: String
        get() = typeName

    fun toJsonString(): String = JSONObject()
        .put("schemaVersion", SCHEMA_VERSION)
        .put("typeCode", typeCode)
        .put("typeName", typeName)
        .put("totalPercent", totalPercent)
        .put("blamePercent", blamePercent)
        .put("belittlingPercent", belittlingPercent)
        .put("demandPercent", demandPercent)
        .put("entitlementPercent", entitlementPercent)
        .put("interferencePercent", interferencePercent)
        .put("explanation", explanation)
        .put("empathyMessage", empathyMessage)
        .put("analyzedAtEpochMillis", analyzedAtEpochMillis)
        .toString()

    companion object {
        private const val SCHEMA_VERSION = 3

        fun fromJsonString(value: String): JinsangAnalysis? =
            runCatching {
                val json = JSONObject(value)
                val schemaVersion = json.optInt("schemaVersion", 1)
                val typeCode = json.optString("typeCode", JinsangType.NOT_REALLY.code)
                val normalizedType = JinsangType.fromCode(typeCode)
                val typeName = if (schemaVersion < SCHEMA_VERSION && normalizedType != null) {
                    normalizedType.displayName
                } else {
                    json.optString("typeName")
                        .ifBlank { normalizedType?.displayName ?: JinsangType.NOT_REALLY.displayName }
                }

                JinsangAnalysis(
                    typeCode = typeCode,
                    typeName = typeName,
                    totalPercent = json.getInt("totalPercent"),
                    blamePercent = json.getInt("blamePercent"),
                    belittlingPercent = json.optInt(
                        "belittlingPercent",
                        json.optInt("comparisonPercent", 0),
                    ),
                    demandPercent = json.optInt(
                        "demandPercent",
                        json.optInt("commandPercent", 0),
                    ),
                    entitlementPercent = json.optInt(
                        "entitlementPercent",
                        json.optInt("lattePercent", 0),
                    ),
                    interferencePercent = json.optInt(
                        "interferencePercent",
                        json.optInt("practicalAdvicePercent", 0),
                    ),
                    explanation = normalizedExplanation(
                        schemaVersion = schemaVersion,
                        typeName = typeName,
                        value = json.optString("explanation"),
                    ),
                    empathyMessage = normalizedEmpathyMessage(
                        schemaVersion = schemaVersion,
                        typeName = typeName,
                        value = json.optString("empathyMessage"),
                    ),
                    analyzedAtEpochMillis = json.optLong("analyzedAtEpochMillis", 0L),
                )
            }.getOrNull()

        private fun normalizedExplanation(
            schemaVersion: Int,
            typeName: String,
            value: String,
        ): String {
            if (schemaVersion >= SCHEMA_VERSION && value.isNotBlank()) return value
            return "$typeName 신호가 감지되었습니다. 이 결과는 알바생이 손님, 고객, 매장 상황에서 들을 수 있는 부당한 말과 감정노동 압박을 기준으로 해석한 것입니다."
        }

        private fun normalizedEmpathyMessage(
            schemaVersion: Int,
            typeName: String,
            value: String,
        ): String {
            if (schemaVersion >= SCHEMA_VERSION && value.isNotBlank()) return value
            return "$typeName 쪽에 가까운 말을 들었네요. 마음이 무거웠다면 너무 자연스러운 반응이에요. 많이 힘드셨죠? 너무 오래 마음에 담아두지는 말아요."
        }
    }
}
