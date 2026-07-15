package org.example.dasi_backend.ai;

/** POST /api/v1/ai/recommendations 요청 바디 */
public record RecommendationRequest(SchoolInput school) {}
