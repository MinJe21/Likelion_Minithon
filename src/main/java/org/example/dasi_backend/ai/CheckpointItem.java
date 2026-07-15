package org.example.dasi_backend.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** 체크포인트 항목 1건 */
public record CheckpointItem(
        @JsonPropertyDescription("고정 카테고리")
        CheckpointCategory category,

        @JsonPropertyDescription("확인 우선순위: HIGH/MEDIUM/LOW")
        Priority priority,

        @JsonPropertyDescription("학교·활용모델에 맞춘 확인 문장 (한국어)")
        String note,

        @JsonPropertyDescription("실무에서 놓치지 말아야 할 참고 팁 (한국어)")
        String tip
) {}
