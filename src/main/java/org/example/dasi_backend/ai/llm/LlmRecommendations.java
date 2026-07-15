package org.example.dasi_backend.ai.llm;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** LLM이 채우는 추천 결과(구조화 출력). id/rank/analysisId 등은 서버가 부여한다. */
public record LlmRecommendations(
        @JsonPropertyDescription("정확히 3개의 추천 활용모델")
        List<Item> recommendations
) {
    public record Item(
            @JsonPropertyDescription("활용모델 이름 (30자 이내, 한국어)")
            String title,
            @JsonPropertyDescription("추천 이유 요약 (100자 이내, 학교 공간·지역·주변자원과 연결, 한국어)")
            String description,
            @JsonPropertyDescription("장점 2개 (한국어 문장)")
            List<String> strengths,
            @JsonPropertyDescription("단점/위험 2개 (한국어 문장)")
            List<String> risks
    ) {}
}
