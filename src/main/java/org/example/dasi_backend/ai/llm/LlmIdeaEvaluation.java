package org.example.dasi_backend.ai.llm;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.example.dasi_backend.ai.Grade;
import org.example.dasi_backend.ai.Metrics;

import java.util.List;

/** LLM이 채우는 아이디어 진단 결과(구조화 출력). evaluationId/schoolId/idea/createdAt 은 서버가 부여. */
public record LlmIdeaEvaluation(
        @JsonPropertyDescription("아이디어 진단 결과 한 문장 요약 (한국어)")
        String summary,
        @JsonPropertyDescription("전체 종합 적합도 등급")
        Grade overallGrade,
        @JsonPropertyDescription("5개 세부 지표. 각 지표는 grade와 reasons(판단 이유 2~3개)를 가진다.")
        Metrics metrics,
        @JsonPropertyDescription("장점 2개 (한국어 문장)")
        List<String> strengths,
        @JsonPropertyDescription("보완해야 할 단점 2개 (한국어 문장)")
        List<String> weaknesses,
        @JsonPropertyDescription("대안 활용모델 이름 2개 (한국어)")
        List<String> alternativeModels,
        @JsonPropertyDescription("최종 보완 방향 제안 (아이디어를 부정하지 말고 실행 가능성을 높이는 구체적 운영 방향, 한국어)")
        String recommendation
) {}
