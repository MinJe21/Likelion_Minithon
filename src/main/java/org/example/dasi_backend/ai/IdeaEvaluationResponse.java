package org.example.dasi_backend.ai;

import java.util.List;

/** POST /api/v1/ai/idea-evaluations 응답 */
public record IdeaEvaluationResponse(
        String evaluationId,
        String schoolId,
        String idea,
        Grade overallGrade,
        String summary,
        Metrics metrics,
        List<String> strengths,
        List<String> weaknesses,
        List<String> alternativeModels,
        String recommendation,
        String createdAt          // ISO 8601
) {}
