package com.ideathon.kondaeshield.analysis

internal data class JinsangEmpathyContext(
    val referenceLines: List<String>,
) {
    val promptBlock: String
        get() = if (referenceLines.isEmpty()) {
            "- 강하게 참고할 직접 발화는 적음. 진상 유형과 점수를 중심으로 공감문만 작성."
        } else {
            referenceLines.joinToString("\n") { "- $it" }
        }

    companion object {
        fun fromCustomerTranscript(customerTranscript: String): JinsangEmpathyContext {
            val scoredSentences = splitSentences(customerTranscript)
                .mapIndexedNotNull { index, sentence ->
                    val score = sentence.jinsangKeywordScore()
                    if (score <= 0) return@mapIndexedNotNull null
                    ScoredSentence(
                        index = index,
                        sentence = sentence.cleanForPrompt(),
                        score = score,
                    )
                }

            val referenceLines = scoredSentences
                .sortedWith(
                    compareByDescending<ScoredSentence> { it.score }
                        .thenBy { it.index },
                )
                .take(MAX_REFERENCE_SENTENCES)
                .sortedBy { it.index }
                .map { it.sentence }

            return JinsangEmpathyContext(referenceLines = referenceLines)
        }

        private fun splitSentences(transcript: String): List<String> =
            transcript
                .split(Regex("[.!?。！？\\n]+"))
                .map { it.trim() }
                .filter { it.isNotBlank() }

        private fun String.jinsangKeywordScore(): Int {
            val keywordScore = JINSANG_RULES.sumOf { rule ->
                countOccurrences(rule.phrase) * rule.phrase.meaningfulLength() * rule.weight
            }
            if (keywordScore == 0) return 0

            val intensifierBonus = if (JinsangKeywordRules.intensifiers.any { contains(it) }) {
                INTENSIFIER_BONUS
            } else {
                0
            }
            return keywordScore + intensifierBonus
        }

        private fun String.countOccurrences(phrase: String): Int {
            if (isBlank() || phrase.isBlank()) return 0

            var count = 0
            var startIndex = 0
            while (true) {
                val index = indexOf(phrase, startIndex, ignoreCase = true)
                if (index < 0) break
                count += 1
                startIndex = index + phrase.length
            }
            return count
        }

        private fun String.meaningfulLength(): Int =
            count { !it.isWhitespace() }

        private fun String.cleanForPrompt(): String =
            replace(Regex("\\s+"), " ")
                .trim()
                .let { cleaned ->
                    if (cleaned.length <= MAX_REFERENCE_SENTENCE_CHARS) {
                        cleaned
                    } else {
                        cleaned.take(MAX_REFERENCE_SENTENCE_CHARS).trimEnd() + "..."
                    }
                }

        private data class ScoredSentence(
            val index: Int,
            val sentence: String,
            val score: Int,
        )

        private val JINSANG_RULES = listOf(
            JinsangKeywordRules.blame,
            JinsangKeywordRules.belittling,
            JinsangKeywordRules.demand,
            JinsangKeywordRules.entitlement,
            JinsangKeywordRules.interference,
        ).flatten()

        private const val MAX_REFERENCE_SENTENCES = 3
        private const val MAX_REFERENCE_SENTENCE_CHARS = 120
        private const val INTENSIFIER_BONUS = 8
    }
}
