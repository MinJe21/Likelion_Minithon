package org.example.dasi_backend.ai;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 세부 평가 항목.
 * 점수(score 1~5)와 등급(grade)은 규칙 엔진이 계산하며, null 이면 미확인.
 * reasons 는 LLM이 계산 결과를 설명한 문장(2~3개).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record MetricDetail(
        Grade grade,            // 미확인이면 null
        Integer score,          // 1~5, 미확인이면 null
        Integer weight,         // 가중치(%)
        Double weightedScore,   // 가중 점수
        List<String> reasons    // 판단 이유 2~3개
) {}
