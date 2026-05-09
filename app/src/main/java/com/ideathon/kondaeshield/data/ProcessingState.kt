package com.ideathon.kondaeshield.data

enum class ProcessingState(val label: String) {
    RECORDING("녹음 중"),
    TRANSCRIBING("전사 중"),
    SUMMARIZING("요약 중"),
    COMPLETE("완료"),
    FAILED("실패"),
}
