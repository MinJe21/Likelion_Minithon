package org.example.dasi_backend.ai;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 최종 체크포인트 요청 시 전달되는, 사용자가 선택한 추천 활용모델.
 * (추천 API 응답의 id/strengths/risks 명칭도 함께 허용하도록 @JsonAlias 적용)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SelectedRecommendation(
        @JsonAlias("id") String recommendationId,
        Integer rank,
        String title,
        String description,
        @JsonAlias("strengths") List<String> advantages,
        @JsonAlias("risks") List<String> limitations
) {}
