package org.example.dasi_backend.ai;

import java.util.List;

/** POST /api/v1/ai/recommendations 응답 */
public record RecommendationResponse(
        String analysisId,
        String schoolId,
        List<RecommendationItem> recommendations,
        String createdAt          // ISO 8601
) {}
