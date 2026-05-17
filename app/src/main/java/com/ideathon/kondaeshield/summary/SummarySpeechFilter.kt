package com.ideathon.kondaeshield.summary

internal object SummarySpeechFilter {
    fun extractRelevantSpeech(
        transcript: String,
        mode: SummaryMode,
    ): String {
        if (transcript.isBlank()) return transcript

        val labelledSpeech = extractLabelledSpeech(transcript)
        if (labelledSpeech.hasSpeakerLabels) {
            val selected = labelledSpeech.lines.filter { line ->
                when (mode) {
                    SummaryMode.BUSINESS ->
                        line.role == SpeakerRole.CUSTOMER ||
                            line.role == SpeakerRole.MANAGER ||
                            (line.role == SpeakerRole.STAFF && line.content.hasBusinessSignal())

                    SummaryMode.COMFORT ->
                        line.role == SpeakerRole.CUSTOMER ||
                            line.role == SpeakerRole.MANAGER
                }
            }
            return selected.joinToString("\n") { it.content }.ifBlank { transcript }
        }

        val selectedSentences = splitSentences(transcript).filter { sentence ->
            when (mode) {
                SummaryMode.BUSINESS ->
                    sentence.hasBusinessSignal() && !sentence.looksLikeOnlyStaffResponse()

                SummaryMode.COMFORT ->
                    sentence.hasCareSignal() && !sentence.looksLikeOnlyStaffResponse()
            }
        }

        return selectedSentences.joinToString("\n").ifBlank { transcript }
    }

    private fun extractLabelledSpeech(transcript: String): LabelledSpeech {
        var hasSpeakerLabels = false
        var previousRole: SpeakerRole? = null
        val lines = mutableListOf<LabelledLine>()

        transcript
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                val parsed = parseLabelledLine(line)
                if (parsed == null) {
                    previousRole?.let { role ->
                        lines += LabelledLine(
                            role = role,
                            content = line,
                        )
                    }
                    return@forEach
                }

                hasSpeakerLabels = true
                previousRole = parsed.role
                if (parsed.content.isNotBlank()) {
                    lines += parsed
                }
            }

        return LabelledSpeech(
            hasSpeakerLabels = hasSpeakerLabels,
            lines = lines,
        )
    }

    private fun parseLabelledLine(line: String): LabelledLine? {
        val match = SPEAKER_LABEL_REGEX.find(line) ?: return null
        val speaker = match.groupValues[1].trim()
        val role = speaker.toSpeakerRole() ?: return null
        return LabelledLine(
            role = role,
            content = match.groupValues[2].trim(),
        )
    }

    private fun String.toSpeakerRole(): SpeakerRole? {
        val label = replace(" ", "").lowercase()
        return when {
            MANAGER_LABELS.any { label.contains(it) } -> SpeakerRole.MANAGER
            STAFF_LABELS.any { label.contains(it) } -> SpeakerRole.STAFF
            CUSTOMER_LABELS.any { label.contains(it) } -> SpeakerRole.CUSTOMER
            else -> null
        }
    }

    private fun splitSentences(transcript: String): List<String> =
        transcript
            .split(Regex("[.!?。！？\\n]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun String.hasBusinessSignal(): Boolean =
        BUSINESS_SIGNAL_PHRASES.any { contains(it, ignoreCase = true) } ||
            BUSINESS_REQUEST_REGEX.containsMatchIn(this) ||
            NUMBER_REGEX.containsMatchIn(this)

    private fun String.hasCareSignal(): Boolean =
        CARE_SIGNAL_PHRASES.any { contains(it, ignoreCase = true) } ||
            CARE_REQUEST_REGEX.containsMatchIn(this)

    private fun String.looksLikeOnlyStaffResponse(): Boolean {
        val hasStaffResponse = STAFF_RESPONSE_PHRASES.any { contains(it, ignoreCase = true) }
        if (!hasStaffResponse) return false

        val hasStrongTask = STRONG_BUSINESS_SIGNAL_PHRASES.any { contains(it, ignoreCase = true) }
        val hasStrongCare = STRONG_CARE_SIGNAL_PHRASES.any { contains(it, ignoreCase = true) }
        return !hasStrongTask && !hasStrongCare
    }

    private data class LabelledSpeech(
        val hasSpeakerLabels: Boolean,
        val lines: List<LabelledLine>,
    )

    private data class LabelledLine(
        val role: SpeakerRole,
        val content: String,
    )

    private enum class SpeakerRole {
        CUSTOMER,
        STAFF,
        MANAGER,
    }

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
        "캐셔",
        "서버",
        "바리스타",
        "파트타이머",
        "staff",
        "employee",
    )

    private val MANAGER_LABELS = listOf(
        "매니저",
        "관리자",
        "사장",
        "점장",
        "manager",
        "owner",
        "supervisor",
    )

    private val BUSINESS_SIGNAL_PHRASES = listOf(
        "주문",
        "포장",
        "배달",
        "환불",
        "교환",
        "결제",
        "취소",
        "메뉴",
        "추가",
        "빼고",
        "넣어",
        "먼저",
        "우선",
        "마감",
        "정리",
        "쓰레기",
        "냉장고",
        "청소",
        "재고",
        "확인",
        "준비",
        "완료",
        "오늘",
        "내일",
        "까지",
        "주세요",
        "해줘",
        "해야",
    )

    private val STRONG_BUSINESS_SIGNAL_PHRASES = listOf(
        "주문",
        "포장",
        "환불",
        "교환",
        "결제",
        "취소",
        "추가",
        "마감",
        "정리",
        "청소",
        "재고",
    )

    private val CARE_SIGNAL_PHRASES = listOf(
        "왜",
        "답답",
        "느려",
        "못해",
        "똑바로",
        "제대로",
        "불친절",
        "알바 주제",
        "직원 주제",
        "손님",
        "매니저",
        "사장",
        "리뷰",
        "민원",
        "신고",
        "본사",
        "단골",
        "서비스",
        "무조건",
        "당장",
        "빨리",
        "규정이 어딨어",
    )

    private val STRONG_CARE_SIGNAL_PHRASES = listOf(
        "알바 주제",
        "직원 주제",
        "리뷰",
        "민원",
        "신고",
        "본사",
        "규정이 어딨어",
        "못해",
        "불친절",
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

    private val BUSINESS_REQUEST_REGEX = Regex("(주세요|해줘|해요|해야|부탁|요청|원함|바람|필요)")
    private val CARE_REQUEST_REGEX = Regex("(불러|환불|리뷰|민원|신고|본사|컴플레인|서비스)")
    private val NUMBER_REGEX = Regex("\\d+|한|두|세|네|다섯|여섯|일곱|여덟|아홉|열")
}
