package com.ideathon.kondaeshield.analysis

import kotlin.math.roundToInt

object JinsangAnalyzer {
    fun analyze(transcript: String): JinsangAnalysis {
        val targetTranscript = JinsangSpeechFilter.extractCustomerSpeech(transcript)
        val sentences = splitSentences(targetTranscript)
        val totalTextLength = targetTranscript.meaningfulLength()
        val blame = score(sentences, totalTextLength, JinsangKeywordRules.blame)
        val belittling = score(sentences, totalTextLength, JinsangKeywordRules.belittling)
        val demand = score(sentences, totalTextLength, JinsangKeywordRules.demand)
        val entitlement = score(sentences, totalTextLength, JinsangKeywordRules.entitlement)
        val interference = score(sentences, totalTextLength, JinsangKeywordRules.interference)

        val totalPercent = (
            blame.percent * 0.25f +
                belittling.percent * 0.20f +
                demand.percent * 0.25f +
                entitlement.percent * 0.20f +
                interference.percent * 0.10f
            ).roundToInt().coerceIn(0, 100)

        val type = decideType(
            totalPercent = totalPercent,
            blamePercent = blame.percent,
            belittlingPercent = belittling.percent,
            demandPercent = demand.percent,
            entitlementPercent = entitlement.percent,
            interferencePercent = interference.percent,
            negativeMatches = blame.matches + belittling.matches + demand.matches +
                entitlement.matches + interference.matches,
        )

        return JinsangAnalysis(
            typeCode = type.code,
            typeName = type.displayName,
            totalPercent = totalPercent,
            blamePercent = blame.percent,
            belittlingPercent = belittling.percent,
            demandPercent = demand.percent,
            entitlementPercent = entitlement.percent,
            interferencePercent = interference.percent,
            explanation = explanationFor(type),
            empathyMessage = empathyMessageFor(type),
            analyzedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    private fun decideType(
        totalPercent: Int,
        blamePercent: Int,
        belittlingPercent: Int,
        demandPercent: Int,
        entitlementPercent: Int,
        interferencePercent: Int,
        negativeMatches: Int,
    ): JinsangType {
        if (negativeMatches == 0 || totalPercent < 5) return JinsangType.GENTLE
        if (totalPercent < 20) return JinsangType.NOT_REALLY

        val negativeScores = listOf(
            NegativeAxis(JinsangType.BLAME, blamePercent),
            NegativeAxis(JinsangType.BELITTLING, belittlingPercent),
            NegativeAxis(JinsangType.DEMAND, demandPercent),
            NegativeAxis(JinsangType.ENTITLEMENT, entitlementPercent),
            NegativeAxis(JinsangType.INTERFERENCE, interferencePercent),
        )
        val highAxisCount = negativeScores.count { it.percent >= 45 }
        if (totalPercent >= 65 && highAxisCount >= 3) return JinsangType.MIXED

        return negativeScores.maxBy { it.percent }.type
    }

    private fun score(
        sentences: List<String>,
        totalTextLength: Int,
        rules: List<KeywordRule>,
        includeIntensifierBonus: Boolean = true,
    ): CategoryScore {
        var matches = 0
        var weightedMatchedTextLength = 0f
        var matchedSentences = 0

        sentences.forEach { sentence ->
            var sentenceMatched = false
            var sentenceWeightedMatchedTextLength = 0f
            rules.forEach { rule ->
                val count = sentence.countOccurrences(rule.phrase)
                if (count > 0) {
                    matches += count
                    sentenceWeightedMatchedTextLength +=
                        count * rule.phrase.meaningfulLength() * rule.weight
                    sentenceMatched = true
                }
            }
            if (sentenceMatched) {
                matchedSentences += 1
                if (includeIntensifierBonus && sentence.hasIntensifier()) {
                    sentenceWeightedMatchedTextLength *= INTENSIFIER_MULTIPLIER
                }
                weightedMatchedTextLength += sentenceWeightedMatchedTextLength
            }
        }

        val keywordDensityScore = (
            weightedMatchedTextLength / totalTextLength.coerceAtLeast(1) *
                KEYWORD_DENSITY_SCALE
            ).roundToInt()
        val sentenceBonus = (
            matchedSentences.toFloat() / sentences.size.coerceAtLeast(1) *
                SENTENCE_BONUS_MAX
            ).roundToInt()
        val percent = (keywordDensityScore + sentenceBonus).coerceIn(0, 100)
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
        JinsangKeywordRules.intensifiers.any { contains(it, ignoreCase = true) }

    private fun String.meaningfulLength(): Int =
        count { !it.isWhitespace() }

    private fun explanationFor(type: JinsangType): String = when (type) {
        JinsangType.GENTLE ->
            "부당한 요구나 하대 표현은 거의 보이지 않습니다. 고객이나 매장 구성원이 상황을 배려 있게 설명한 천사손님 대화에 가깝습니다."

        JinsangType.NOT_REALLY ->
            "강한 진상 패턴이라고 보긴 어렵지만, 알바생 입장에서는 살짝 압박으로 남을 수 있는 말이 섞여 있습니다. 작은 불편감도 감정노동의 신호일 수 있습니다."

        JinsangType.BLAME ->
            "문제 해결보다 알바생에게 책임을 몰아가는 흐름입니다. 실수 여부와 별개로, 사람을 탓하는 표현이 앞서면 방어감과 위축감이 크게 남을 수 있습니다."

        JinsangType.BELITTLING ->
            "직원이나 알바생을 낮춰 부르거나 다른 매장, 다른 직원과 비교하는 흐름입니다. 요청의 내용보다 말의 태도가 상대를 작게 만드는 쪽에 가깝습니다."

        JinsangType.DEMAND ->
            "규정이나 상황 설명을 건너뛰고 즉시 처리, 예외, 무료 제공을 밀어붙이는 무리한 요구가 두드러집니다. 응대자가 통제권을 빼앗긴 듯 느끼기 쉬운 대화입니다."

        JinsangType.INTERFERENCE ->
            "도움이나 조언처럼 들리지만 실제로는 응대 방식과 감정 표현을 통제하는 간섭에 가깝습니다. 알바생의 상황보다 '이렇게 해야 한다'는 기준이 앞섭니다."

        JinsangType.ENTITLEMENT ->
            "단골, 민원, 리뷰, 관리자 호출 같은 지위를 압박 수단으로 쓰는 갑질성 흐름입니다. 요청을 넘어 상대를 겁주거나 굴복시키려는 신호가 강합니다."

        JinsangType.MIXED ->
            "책임전가, 하대, 무리한 요구, 갑질성 압박이 여러 방향으로 섞인 대화입니다. 듣는 사람이 예민한 게 아니라 감정노동 부담이 큰 상황에 가깝습니다."
    }

    private fun empathyMessageFor(type: JinsangType): String = when (type) {
        JinsangType.GENTLE ->
            "천사손님에 가까운 말을 들었네요. 바쁜 와중에 이런 배려가 느껴지면 조금은 마음이 놓였을 것 같아요."

        JinsangType.NOT_REALLY ->
            "조금 애매하지만 은근히 신경 쓰이는 말을 들었네요. 크게 화낼 일은 아니어도 마음 한쪽이 찝찝했을 수 있어요."

        JinsangType.BLAME ->
            "책임전가형 진상에게 탓하는 말을 들었네요. 많이 억울하고 위축됐을 것 같아요. 너무 마음에 오래 담아두지는 말아요."

        JinsangType.BELITTLING ->
            "하대비교형 진상에게 무시하거나 비교하는 말을 들었네요. 순간적으로 자존심이 상하고 속상했을 것 같아요. 그런 말까지 마음속에 크게 남기진 않았으면 해요."

        JinsangType.DEMAND ->
            "무리요구형 진상에게 부담스러운 요구를 들었네요. 계속 몰아붙이는 말투라 많이 답답하고 지쳤을 것 같아요."

        JinsangType.INTERFERENCE ->
            "간섭훈수형 진상에게 이래라저래라 하는 말을 들었네요. 겉으론 조언 같아도 듣는 입장에선 꽤 피곤했을 것 같아요."

        JinsangType.ENTITLEMENT ->
            "갑질특권형 진상에게 압박하는 말을 들었네요. 리뷰나 민원 얘기까지 나오면 마음이 철렁했을 것 같아요. 정말 많이 힘드셨죠."

        JinsangType.MIXED ->
            "복합형 진상에게 여러 방향으로 몰아붙이는 말을 들었네요. 한 번에 많이 맞은 느낌이라 지치고 억울했을 것 같아요. 너무 마음에 담아두지는 말아요."
    }

    private data class CategoryScore(
        val percent: Int,
        val matches: Int,
    )

    private data class NegativeAxis(
        val type: JinsangType,
        val percent: Int,
    )

    private const val KEYWORD_DENSITY_SCALE = 300f
    private const val SENTENCE_BONUS_MAX = 20f
    private const val INTENSIFIER_MULTIPLIER = 1.25f
}
