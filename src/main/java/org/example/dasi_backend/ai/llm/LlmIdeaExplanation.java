package org.example.dasi_backend.ai.llm;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * LLM은 규칙 엔진이 계산한 점수/등급을 "설명"만 한다(점수를 바꾸지 않음).
 * 각 지표 reasons 는 주어진 등급과 일관된 판단 이유 2~3개.
 */
public record LlmIdeaExplanation(
        @JsonPropertyDescription("종합 평가 한 문장 요약 (주어진 종합 등급/점수와 일관되게, 한국어)")
        String summary,
        @JsonPropertyDescription("지표별 판단 이유. 각 항목 2~3개의 한국어 문장, 주어진 등급과 일관되게. 미확인 항목은 무엇을 확인해야 하는지 설명.")
        Reasons reasons,
        @JsonPropertyDescription("장점 2개")
        List<String> strengths,
        @JsonPropertyDescription("단점/보완점 2개")
        List<String> weaknesses,
        @JsonPropertyDescription("대안 활용모델 이름 2개")
        List<String> alternativeModels,
        @JsonPropertyDescription("실행 가능성을 높이는 구체적 보완 방향 (한국어)")
        String recommendation
) {
    public record Reasons(
            List<String> spaceSuitability,
            List<String> accessibility,
            List<String> regionalDemand,
            List<String> similarCaseSuitability,
            List<String> executionFeasibility
    ) {}
}
