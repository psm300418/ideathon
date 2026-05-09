package com.ideathon.kondaeshield.analysis

data class NaggingAnalysis(
    val blamePercent: Int,
    val comparisonPercent: Int,
    val practicalAdvicePercent: Int,
    val resultLabel: String,
    val explanation: String,
)
