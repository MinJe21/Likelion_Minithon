package org.example.dasi_backend.ai;

import java.util.List;

/** 추천 활용모델 1건 (응답) */
public record RecommendationItem(
        String id,               // recommendation_01 ...
        int rank,                // 1, 2, 3
        String title,
        String description,
        List<String> strengths,
        List<String> risks
) {}
