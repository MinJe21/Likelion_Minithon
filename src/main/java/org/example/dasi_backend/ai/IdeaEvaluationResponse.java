package org.example.dasi_backend.ai;

import java.util.List;

/**
 * POST /api/v1/ai/idea-evaluations 응답.
 * overallGrade/metrics 는 기존 계약 유지(하위호환), 규칙 기반 점수 필드
 * (overallScore/coverage/statusCode/statusLabel, 지표별 score/weight/weightedScore)를 추가로 포함.
 */
public record IdeaEvaluationResponse(
        String evaluationId,
        String schoolId,
        String idea,
        Grade overallGrade,       // overallScore 구간에서 파생 (INSUFFICIENT_DATA면 null)
        Integer overallScore,     // 0~100, 판단 제한이면 null
        Integer coverage,         // 확인된 가중치 합(%)
        String statusCode,        // HIGH_BASIC_FIT / CONDITIONAL_REVIEW / ...
        String statusLabel,
        String summary,
        Metrics metrics,
        List<String> strengths,
        List<String> weaknesses,
        List<String> alternativeModels,
        String recommendation,
        String createdAt
) {}
