package org.example.dasi_backend.ai.scoring;

/** Core 진단 규칙 계산 결과 (PDF 명세). */
public record CoreScore(
        Integer overallScore,   // 0~100, INSUFFICIENT_DATA 이면 null
        int coverage,           // 확인된 가중치 합(%)
        String statusCode,      // HIGH_BASIC_FIT / CONDITIONAL_REVIEW / MAJOR_CHECKS_REQUIRED / LIMITED_FIT / INSUFFICIENT_DATA
        String statusLabel,
        CriterionScore spaceFit,
        CriterionScore accessibility,
        CriterionScore regionalDemand,
        CriterionScore similarCaseFit,
        CriterionScore executionRisk
) {}
