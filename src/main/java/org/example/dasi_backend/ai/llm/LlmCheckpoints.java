package org.example.dasi_backend.ai.llm;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.example.dasi_backend.ai.CheckpointItem;

import java.util.List;

/** LLM이 채우는 체크포인트 결과(구조화 출력). 6개 카테고리 항목. */
public record LlmCheckpoints(
        @JsonPropertyDescription("6개 카테고리(ADMINISTRATION, FACILITY, BUDGET, DEMAND, OPERATING_SUSTAINABILITY, COMMUNITY_ACCEPTANCE) 각각에 대한 체크포인트 항목 6개")
        List<CheckpointItem> items
) {}
