package com.ideathon.kondaeshield.analysis

import kotlin.math.roundToInt

object NaggingAnalyzer {
    fun analyze(transcript: String): NaggingAnalysis {
        val sentences = splitSentences(transcript)
        val blame = score(sentences, NaggingKeywordRules.blame)
        val comparison = score(sentences, NaggingKeywordRules.comparison)
        val command = score(sentences, NaggingKeywordRules.command)
        val latte = score(sentences, NaggingKeywordRules.latte)
        val advice = score(sentences, NaggingKeywordRules.practicalAdvice, includeIntensifierBonus = false)

        val totalPercent = (
            blame.percent * 0.30f +
                comparison.percent * 0.25f +
                command.percent * 0.25f +
                latte.percent * 0.20f
            ).roundToInt().coerceIn(0, 100)

        val type = decideType(
            totalPercent = totalPercent,
            blamePercent = blame.percent,
            comparisonPercent = comparison.percent,
            commandPercent = command.percent,
            lattePercent = latte.percent,
            practicalAdvicePercent = advice.percent,
            negativeMatches = blame.matches + comparison.matches + command.matches + latte.matches,
        )

        return NaggingAnalysis(
            typeCode = type.code,
            typeName = type.displayName,
            totalPercent = totalPercent,
            blamePercent = blame.percent,
            comparisonPercent = comparison.percent,
            commandPercent = command.percent,
            lattePercent = latte.percent,
            practicalAdvicePercent = advice.percent,
            explanation = explanationFor(type),
            rewriteSuggestion = rewriteSuggestionFor(type),
        )
    }

    private fun decideType(
        totalPercent: Int,
        blamePercent: Int,
        comparisonPercent: Int,
        commandPercent: Int,
        lattePercent: Int,
        practicalAdvicePercent: Int,
        negativeMatches: Int,
    ): NaggingType {
        if (negativeMatches == 0 || totalPercent < 5) return NaggingType.GENTLE
        if (totalPercent < 20) return NaggingType.NOT_REALLY

        val negativeScores = listOf(
            NegativeAxis(NaggingType.BLAME, blamePercent),
            NegativeAxis(NaggingType.COMPARISON, comparisonPercent),
            NegativeAxis(NaggingType.COMMAND, commandPercent),
            NegativeAxis(NaggingType.LATTE, lattePercent),
        )
        val highAxisCount = negativeScores.count { it.percent >= 45 }
        if (totalPercent >= 65 && highAxisCount >= 3) return NaggingType.MIXED

        val strongestNegative = negativeScores.maxBy { it.percent }
        if (practicalAdvicePercent >= 45 && practicalAdvicePercent >= strongestNegative.percent) {
            return NaggingType.ADVICE
        }

        return strongestNegative.type
    }

    private fun score(
        sentences: List<String>,
        rules: List<KeywordRule>,
        includeIntensifierBonus: Boolean = true,
    ): CategoryScore {
        var matches = 0
        var weightedMatches = 0
        var matchedSentences = 0

        sentences.forEach { sentence ->
            var sentenceMatched = false
            rules.forEach { rule ->
                val count = sentence.countOccurrences(rule.phrase)
                if (count > 0) {
                    matches += count
                    weightedMatches += count * rule.weight
                    sentenceMatched = true
                }
            }
            if (sentenceMatched) {
                matchedSentences += 1
                if (includeIntensifierBonus && sentence.hasIntensifier()) {
                    weightedMatches += 1
                }
            }
        }

        val percent = ((weightedMatches * KEYWORD_WEIGHT) + (matchedSentences * SENTENCE_WEIGHT))
            .coerceIn(0, 100)
        return CategoryScore(
            percent = percent,
            matches = matches,
        )
    }

    private fun splitSentences(transcript: String): List<String> =
        transcript
            .split(Regex("[.!?。！？\\n]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(transcript.trim()).filter { it.isNotBlank() } }

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

    private fun String.hasIntensifier(): Boolean =
        NaggingKeywordRules.intensifiers.any { contains(it, ignoreCase = true) }

    private fun explanationFor(type: NaggingType): String = when (type) {
        NaggingType.GENTLE ->
            "이 정도면 공격적인 잔소리라기보다 차분한 조언에 가깝습니다. 듣는 사람을 몰아붙이는 표현은 거의 보이지 않아서, 마음에 오래 남을 말은 적어 보입니다."

        NaggingType.NOT_REALLY ->
            "꼰대라고 딱지를 붙이기엔 아직 애매합니다. 다만 살짝 거슬릴 수 있는 표현이 섞여 있어서, 들은 사람이 괜히 찝찝했다면 그 감각도 꽤 정상입니다."

        NaggingType.BLAME ->
            "문제 해결보다 책임 추궁이 앞서는 말투입니다. 이런 표현은 도움보다 방어감부터 만들기 쉽고, 듣는 사람에게 '내가 뭘 그렇게까지 잘못했지?'라는 피로감을 남깁니다."

        NaggingType.COMPARISON ->
            "이 대화는 조언이라기보다 비교 압박에 가깝습니다. 남이나 과거의 기준을 들이대는 말은 사람을 성장시키기보다 위축시키기 쉽습니다."

        NaggingType.COMMAND ->
            "설명보다 통제가 앞서는 흐름입니다. 대화라기보다 지시문에 가까워서, 듣는 사람 입장에서는 존중받기보다 눌리는 느낌이 강하게 남을 수 있습니다."

        NaggingType.ADVICE ->
            "조언처럼 포장되어 있지만, 상대 입장을 묻기보다 답을 먼저 던지는 훈수에 가깝습니다. 좋은 의도였더라도 듣는 사람은 '내 얘기는 안 듣네'라고 느끼기 쉽습니다."

        NaggingType.LATTE ->
            "과거 경험이 조언이 아니라 권위처럼 사용되는 라떼형 흐름입니다. '내가 해봐서 아는데'가 반복되면 지금 힘든 사람에게는 공감보다 압박으로 들립니다."

        NaggingType.MIXED ->
            "비난, 비교, 지시가 한꺼번에 섞인 꽤 피곤한 대화입니다. 듣는 사람이 예민한 게 아니라, 여러 방향에서 압박을 받은 상황에 가깝습니다."
    }

    private fun rewriteSuggestionFor(type: NaggingType): String = when (type) {
        NaggingType.GENTLE ->
            "\"괜찮아, 어디서 막혔는지 같이 보자\"처럼 지금의 차분한 톤을 유지하면 좋습니다."

        NaggingType.NOT_REALLY ->
            "\"왜 그랬어?\"보다 \"어느 부분이 어려웠어?\"라고 바꾸면 훨씬 덜 찝찝하게 들립니다."

        NaggingType.BLAME ->
            "\"왜 이것도 못 해?\"보다 \"어디서 막혔는지 먼저 보자\"가 훨씬 덜 상처가 됩니다."

        NaggingType.COMPARISON ->
            "\"남들은 하는데\"보다 \"너한테 맞는 방법을 다시 찾아보자\"가 훨씬 안전합니다."

        NaggingType.COMMAND ->
            "\"그냥 시키는 대로 해\"보다 \"이 방식이 필요한 이유를 설명해볼게\"가 덜 눌리게 들립니다."

        NaggingType.ADVICE ->
            "\"내 말대로 해\"보다 \"내가 보기엔 이런 방법도 있는데, 너 생각은 어때?\"가 훨씬 낫습니다."

        NaggingType.LATTE ->
            "\"나 때는 말이야\"보다 \"내 경험은 이랬는데, 지금 상황에는 다를 수 있어\"가 덜 피곤합니다."

        NaggingType.MIXED ->
            "\"왜 못 해, 남들은 하는데 그냥 해\"보다 \"무엇이 막혔고 어떤 도움이 필요한지 보자\"로 바꾸는 게 맞습니다."
    }

    private data class CategoryScore(
        val percent: Int,
        val matches: Int,
    )

    private data class NegativeAxis(
        val type: NaggingType,
        val percent: Int,
    )

    private const val KEYWORD_WEIGHT = 12
    private const val SENTENCE_WEIGHT = 4
}
