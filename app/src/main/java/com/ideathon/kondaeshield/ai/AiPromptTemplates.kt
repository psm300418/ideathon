package com.ideathon.kondaeshield.ai

import com.ideathon.kondaeshield.analysis.JinsangAnalysis
import com.ideathon.kondaeshield.analysis.JinsangEmpathyContext
import com.ideathon.kondaeshield.summary.SummaryMode
import com.ideathon.kondaeshield.summary.SummaryPromptContext

internal object AiPromptTemplates {
    val empathyInstructions: String =
        """
        너는 알바생을 위한 앱에 들어갈 짧은 공감 문구를 쓰는 한국어 작성자다.
        목표는 해결책을 제시하는 것이 아니라, 사용자가 들은 부당한 말의 성격을 짚고 감정 부담을 덜어주는 것이다.

        출력 규칙:
        - 최종 문구만 한국어 1~2문장으로 쓴다.
        - 전체 길이는 50~120자 정도로 자연스럽게 쓴다.
        - "~에게 ~말을 들었네요" 흐름을 포함한다.
        - 마지막은 "많이 힘드셨죠", "너무 마음에 담아두지는 말아요", "그 말에 마음이 무거웠겠어요"처럼 공감으로 끝낸다.
        - 참고 발화는 의미만 사용하고, 원문 문장을 그대로 복사하거나 따옴표로 인용하지 않는다.
        - 특정 세대, 나이, 성별을 겨냥하지 않는다.

        절대 쓰지 말 것:
        - 대응 방법, 행동 지시, 법률/노무 조언, 업무 개선 조언
        - 녹취록, 화자 라벨, 제목, bullet, 번호, 따옴표, 콜론
        - "친절했다", "정당한 요청이다"처럼 원문을 미화하는 표현
        - 참고 문장의 욕설, 비하어, 압박 표현을 그대로 반복하는 표현

        나쁜 출력:
        손님: 알바 주제에 결정 못 하면 매니저 불러요. 많이 힘드셨죠.

        좋은 출력:
        갑질특권형 진상에게 무시와 압박이 섞인 말을 들었네요. 순간적으로 마음이 철렁하고 많이 힘드셨죠.
        """.trimIndent()

    fun buildEmpathyPrompt(
        empathyContext: JinsangEmpathyContext,
        analysis: JinsangAnalysis,
    ): String =
        """
        분석 결과:
        - 진상 유형: ${analysis.typeName}
        - 진상력: ${analysis.totalPercent}%
        - 강한 신호: ${analysis.strongSignalSummary()}

        참고 발화 후보:
        ${empathyContext.promptBlock}

        작성 지침:
        - 참고 발화의 표현을 복사하지 말고 "무시", "압박", "비난", "무리한 요구", "감정 소모" 같은 추상 표현으로 바꿔라.
        - 진상 유형이 "천사손님"이면 진상이라고 부르지 말고 "손님" 또는 "고객"으로 부드럽게 표현해라.
        - 상황 분석 1문장과 감정 공감 1문장만 남겨라.
        - 해결책이나 다음 행동은 쓰지 마라.
        """.trimIndent()

    fun summaryInstructions(mode: SummaryMode): String =
        when (mode) {
            SummaryMode.BUSINESS ->
                """
                너는 알바생을 위한 매장 업무 요약 작성자다.
                입력은 전체 녹취록이 아니라 전처리된 핵심 후보 문장이다.
                목표는 사용자가 지금 해야 할 일, 주문/요청 내용, 수량, 시간, 우선순위를 빠르게 확인하게 하는 것이다.

                판단 기준:
                - 행동으로 옮길 수 있는 내용만 남긴다.
                - 메뉴, 수량, 포장/매장, 결제, 환불, 교환, 마감 전 지시처럼 업무 정보가 있으면 보존한다.
                - 감정 표현, 비난, 반말, 압박, 불만 표시는 제거한다.
                - 없는 메뉴, 수량, 시간, 지시를 추측해서 만들지 않는다.
                - 순수한 욕설이나 모욕만 있고 업무 정보가 없으면 생략한다.

                출력 규칙:
                - 반드시 JSON 객체 하나만 출력한다.
                - 첫 글자는 {, 마지막 글자는 } 이어야 한다.
                - 형식: {"items":["업무 항목 1","업무 항목 2"]}
                - items는 최대 6개, 각 항목은 35자 이내의 짧은 명사형 또는 동사형으로 쓴다.
                - markdown, 제목, 설명문, 코드블록은 쓰지 않는다.
                """.trimIndent()

            SummaryMode.COMFORT ->
                """
                너는 알바생을 위한 마음 보호 해석 작성자다.
                입력은 전체 녹취록이 아니라 전처리된 핵심 후보 문장이다.
                목표는 공격적인 어조를 직접 다시 읽지 않아도 상황의 의도만 이해하게 하는 것이다.

                판단 기준:
                - 손님/관리자가 중요하게 여긴 요구, 불만, 기대를 중립적인 말로 바꾼다.
                - 원문의 욕설, 비하, 반말, 압박 표현은 절대 반복하지 않는다.
                - "친절하게 요청함"처럼 실제보다 좋게 왜곡하지 않는다.
                - 해결책, 행동 지시, 법률/노무 조언, 과한 위로는 쓰지 않는다.
                - 순수한 모욕만 있으면 "업무 처리 방식에 강한 불만을 표현함"처럼 의도 수준으로 낮춘다.

                출력 규칙:
                - 반드시 JSON 객체 하나만 출력한다.
                - 첫 글자는 {, 마지막 글자는 } 이어야 한다.
                - 형식: {"interpretations":["해석 1","해석 2"]}
                - interpretations는 최대 5개, 각 항목은 45자 이내로 쓴다.
                - markdown, 제목, 설명문, 코드블록은 쓰지 않는다.
                """.trimIndent()
        }

    fun buildSummaryPrompt(
        context: SummaryPromptContext,
        mode: SummaryMode,
    ): String =
        """
        모드: ${mode.label}

        핵심 후보 문장:
        ${context.promptBlock}

        후보 문장의 의미만 참고해라.
        원문을 길게 복사하지 말고, 지정된 JSON만 출력해라.
        """.trimIndent()

    private fun JinsangAnalysis.strongSignalSummary(): String {
        val signals = listOf(
            "책임전가/비난" to blamePercent,
            "하대/비하" to belittlingPercent,
            "무리한 요구" to demandPercent,
            "갑질/특권 요구" to entitlementPercent,
            "간섭/훈수" to interferencePercent,
        )
            .sortedByDescending { it.second }
            .filter { it.second >= 10 }
            .take(3)
            .joinToString(", ") { "${it.first} ${it.second}%" }

        return signals.ifBlank { "강한 진상 신호 없음" }
    }
}
