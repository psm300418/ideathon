package com.ideathon.kondaeshield.analysis

enum class JinsangType(
    val code: String,
    val displayName: String,
) {
    GENTLE("G", "천사손님"),
    NOT_REALLY("N", "주의 신호"),
    BLAME("B", "책임전가형 진상"),
    BELITTLING("C", "하대비교형 진상"),
    DEMAND("D", "무리요구형 진상"),
    INTERFERENCE("A", "간섭훈수형 진상"),
    ENTITLEMENT("R", "갑질특권형 진상"),
    MIXED("M", "복합형 진상"),
    ;

    companion object {
        fun fromCode(code: String): JinsangType? =
            entries.firstOrNull { it.code == code }
    }
}
