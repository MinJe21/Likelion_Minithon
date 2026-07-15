package org.example.dasi_backend.ai;

/** POST /api/v1/ai/final-checkpoints 요청 바디 */
public record FinalCheckpointRequest(
        String sourceType,                       // RECOMMENDATION | IDEA_EVALUATION
        String analysisId,
        SchoolInput school,
        SelectedRecommendation selectedRecommendation,  // sourceType=RECOMMENDATION 시 필수
        IdeaEvaluationInput ideaEvaluation              // sourceType=IDEA_EVALUATION 시 필수
) {}
