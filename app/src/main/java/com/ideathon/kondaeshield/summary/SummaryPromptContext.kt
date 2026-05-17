package com.ideathon.kondaeshield.summary

internal data class SummaryPromptContext(
    val sourceLines: List<String>,
) {
    val promptBlock: String
        get() = if (sourceLines.isEmpty()) {
            "- 참고할 핵심 문장이 적음. 전체 대화의 목적을 추론하되 없는 내용은 만들지 말 것."
        } else {
            sourceLines.joinToString("\n") { "- $it" }
        }

    companion object {
        fun forMode(
            transcript: String,
            mode: SummaryMode,
        ): SummaryPromptContext {
            val filteredTranscript = SummarySpeechFilter.extractRelevantSpeech(
                transcript = transcript,
                mode = mode,
            )
            val sentences = splitSentences(filteredTranscript)
            val scored = sentences
                .mapIndexedNotNull { index, sentence ->
                    val score = when (mode) {
                        SummaryMode.BUSINESS -> sentence.businessScore()
                        SummaryMode.COMFORT -> sentence.comfortScore()
                    }
                    if (score <= 0) return@mapIndexedNotNull null
                    ScoredSentence(
                        index = index,
                        sentence = sentence.cleanForPrompt(),
                        score = score,
                    )
                }

            val fallback = sentences
                .take(FALLBACK_SENTENCE_COUNT)
                .map { it.cleanForPrompt() }

            val sourceLines = scored
                .sortedWith(
                    compareByDescending<ScoredSentence> { it.score }
                        .thenBy { it.index },
                )
                .take(MAX_SOURCE_SENTENCES)
                .sortedBy { it.index }
                .map { it.sentence }
                .ifEmpty { fallback }

            return SummaryPromptContext(sourceLines = sourceLines)
        }

        private fun splitSentences(transcript: String): List<String> =
            transcript
                .lineSequence()
                .flatMap { line -> line.split(Regex("[.!?。！？]+")) }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toList()

        private fun String.businessScore(): Int {
            val text = withoutSpeakerLabel()
            val keywordScore = BUSINESS_KEYWORDS.sumOf { keyword ->
                if (text.contains(keyword, ignoreCase = true)) BUSINESS_KEYWORD_WEIGHT else 0
            }
            val numberScore = if (NUMBER_REGEX.containsMatchIn(text)) NUMBER_WEIGHT else 0
            val requestScore = if (REQUEST_REGEX.containsMatchIn(text)) REQUEST_WEIGHT else 0
            return keywordScore + numberScore + requestScore
        }

        private fun String.comfortScore(): Int {
            val text = withoutSpeakerLabel()
            val negativeScore = CARE_KEYWORDS.sumOf { keyword ->
                if (text.contains(keyword, ignoreCase = true)) CARE_KEYWORD_WEIGHT else 0
            }
            val requestScore = if (REQUEST_REGEX.containsMatchIn(text)) REQUEST_WEIGHT else 0
            return negativeScore + requestScore
        }

        private fun String.cleanForPrompt(): String =
            withoutSpeakerLabel()
                .replace(Regex("\\s+"), " ")
                .trim()
                .let { cleaned ->
                    if (cleaned.length <= MAX_SOURCE_SENTENCE_CHARS) {
                        cleaned
                    } else {
                        cleaned.take(MAX_SOURCE_SENTENCE_CHARS).trimEnd() + "..."
                    }
                }

        private fun String.withoutSpeakerLabel(): String =
            replace(SPEAKER_LABEL_REGEX, "").trim()

        private data class ScoredSentence(
            val index: Int,
            val sentence: String,
            val score: Int,
        )

        private val BUSINESS_KEYWORDS = listOf(
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
        )

        private val CARE_KEYWORDS = listOf(
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

        private val REQUEST_REGEX = Regex("(주세요|해줘|해요|해야|부탁|요청|원함|바람|필요)")
        private val NUMBER_REGEX = Regex("\\d+|한|두|세|네|다섯|여섯|일곱|여덟|아홉|열")
        private val SPEAKER_LABEL_REGEX = Regex("^\\s*[^:：]{1,18}\\s*[:：]\\s*")

        private const val MAX_SOURCE_SENTENCES = 8
        private const val FALLBACK_SENTENCE_COUNT = 6
        private const val MAX_SOURCE_SENTENCE_CHARS = 140
        private const val BUSINESS_KEYWORD_WEIGHT = 3
        private const val CARE_KEYWORD_WEIGHT = 3
        private const val NUMBER_WEIGHT = 4
        private const val REQUEST_WEIGHT = 2
    }
}
