package com.ideathon.kondaeshield.analysis

enum class NaggingType(
    val code: String,
    val displayName: String,
) {
    GENTLE("G", "상냥한 조언러"),
    NOT_REALLY("N", "꼰대는 아닌듯?"),
    BLAME("B", "비난형 꼰대"),
    COMPARISON("C", "비교형 꼰대"),
    COMMAND("D", "지시형 꼰대"),
    ADVICE("A", "훈수형 꼰대"),
    LATTE("R", "라떼형 꼰대"),
    MIXED("M", "복합형 꼰대"),
}
