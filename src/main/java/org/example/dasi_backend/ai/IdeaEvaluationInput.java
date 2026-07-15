package org.example.dasi_backend.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 최종 체크포인트 요청 시 전달되는, 앞서 수행한 아이디어 진단 결과.
 * (idea-evaluations 응답을 그대로 담아 보낸다. schoolId/createdAt 등 여분 필드는 무시)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IdeaEvaluationInput(
        String evaluationId,
        String idea,
        Grade overallGrade,
        String summary,
        Metrics metrics,
        List<String> strengths,
        List<String> weaknesses,
        List<String> alternativeModels,
        String recommendation
) {}
