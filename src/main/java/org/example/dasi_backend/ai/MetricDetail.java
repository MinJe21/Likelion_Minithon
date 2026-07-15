package org.example.dasi_backend.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** 세부 평가 항목: 등급 + 판단 이유 2~3개 */
public record MetricDetail(
        @JsonPropertyDescription("해당 항목 등급: VERY_LOW/LOW/MEDIUM/HIGH/VERY_HIGH 중 하나")
        Grade grade,

        @JsonPropertyDescription("등급을 판단한 구체적 이유 2~3개 (각 2~3줄 분량의 한국어 문장)")
        List<String> reasons
) {}
