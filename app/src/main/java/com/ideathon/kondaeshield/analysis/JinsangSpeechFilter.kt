package com.ideathon.kondaeshield.analysis

internal object JinsangSpeechFilter {
    fun extractCustomerSpeech(transcript: String): String {
        if (transcript.isBlank()) return transcript

        val labelledSpeech = extractFromSpeakerLabels(transcript)
        if (labelledSpeech.hasSpeakerLabels) {
            return labelledSpeech.customerLines.joinToString("\n")
        }

        val likelyCustomerSentences = splitSentences(transcript)
            .filter(::looksLikeCustomerSpeech)

        return likelyCustomerSentences
            .joinToString("\n")
            .ifBlank { transcript }
    }

    private fun extractFromSpeakerLabels(transcript: String): LabelledSpeech {
        var hasSpeakerLabels = false
        var previousLineWasCustomer = false
        val customerLines = mutableListOf<String>()

        transcript
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                val labelledLine = parseLabelledLine(line)
                if (labelledLine == null) {
                    if (previousLineWasCustomer) {
                        customerLines += line
                    }
                    return@forEach
                }

                hasSpeakerLabels = true
                previousLineWasCustomer = labelledLine.speaker.isCustomerLabel()
                if (previousLineWasCustomer && labelledLine.content.isNotBlank()) {
                    customerLines += labelledLine.content
                }
            }

        return LabelledSpeech(
            hasSpeakerLabels = hasSpeakerLabels,
            customerLines = customerLines,
        )
    }

    private fun parseLabelledLine(line: String): LabelledLine? {
        val match = SPEAKER_LABEL_REGEX.find(line) ?: return null
        val speaker = match.groupValues[1].trim()
        if (!speaker.isCustomerLabel() && !speaker.isStaffLabel()) return null

        return LabelledLine(
            speaker = speaker,
            content = match.groupValues[2].trim(),
        )
    }

    private fun looksLikeCustomerSpeech(sentence: String): Boolean {
        val normalized = sentence.trim()
        if (normalized.isBlank()) return false

        val hasCustomerSignal = CUSTOMER_SIGNAL_PHRASES.any {
            normalized.contains(it, ignoreCase = true)
        }
        if (!hasCustomerSignal) return false

        val hasStaffResponse = STAFF_RESPONSE_PHRASES.any {
            normalized.contains(it, ignoreCase = true)
        }
        val hasStrongCustomerSignal = STRONG_CUSTOMER_SIGNAL_PHRASES.any {
            normalized.contains(it, ignoreCase = true)
        }

        return !hasStaffResponse || hasStrongCustomerSignal
    }

    private fun splitSentences(transcript: String): List<String> =
        transcript
            .split(Regex("[.!?。！？\\n]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun String.isCustomerLabel(): Boolean {
        val label = replace(" ", "")
        if (label.isStaffLabel()) return false
        return CUSTOMER_LABELS.any { label.contains(it) }
    }

    private fun String.isStaffLabel(): Boolean {
        val label = replace(" ", "")
        return STAFF_LABELS.any { label.contains(it) }
    }

    private data class LabelledLine(
        val speaker: String,
        val content: String,
    )

    private data class LabelledSpeech(
        val hasSpeakerLabels: Boolean,
        val customerLines: List<String>,
    )

    private val SPEAKER_LABEL_REGEX = Regex("^\\s*([^:：]{1,18})\\s*[:：]\\s*(.*)$")

    private val CUSTOMER_LABELS = listOf(
        "손님",
        "고객",
        "방문객",
        "게스트",
        "민원인",
        "customer",
        "guest",
        "client",
    )

    private val STAFF_LABELS = listOf(
        "알바",
        "알바생",
        "직원",
        "점원",
        "매니저",
        "관리자",
        "사장",
        "캐셔",
        "서버",
        "바리스타",
        "파트타이머",
        "staff",
        "employee",
        "manager",
    )

    private val CUSTOMER_SIGNAL_PHRASES =
        listOf(
            JinsangKeywordRules.blame,
            JinsangKeywordRules.belittling,
            JinsangKeywordRules.demand,
            JinsangKeywordRules.entitlement,
            JinsangKeywordRules.interference,
        ).flatten().map { it.phrase } +
            listOf(
                "왜",
                "뭐예요",
                "뭐에요",
                "어떻게 할 거예요",
                "해줘요",
                "해주세요",
                "불러요",
                "불러 주세요",
                "환불",
                "교환",
                "컴플레인",
                "리뷰",
                "민원",
                "신고",
                "본사",
                "단골",
                "서비스",
                "손님",
            )

    private val STRONG_CUSTOMER_SIGNAL_PHRASES =
        listOf(
            JinsangKeywordRules.blame,
            JinsangKeywordRules.belittling,
            JinsangKeywordRules.demand,
            JinsangKeywordRules.entitlement,
        ).flatten().map { it.phrase } +
            listOf(
                "사장 불러",
                "매니저 불러",
                "리뷰 남길",
                "민원 넣",
                "신고할",
                "본사",
            )

    private val STAFF_RESPONSE_PHRASES = listOf(
        "죄송합니다",
        "확인해드리겠습니다",
        "확인하겠습니다",
        "안내드리겠습니다",
        "도와드리겠습니다",
        "처리해드리겠습니다",
        "전달드리겠습니다",
        "기다려 주세요",
        "잠시만",
        "규정상",
        "어렵습니다",
        "불편드려",
        "가능합니다",
        "불가능합니다",
        "제가",
        "저희",
        "매장 규정",
    )
}
