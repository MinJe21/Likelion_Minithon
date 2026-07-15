package org.example.dasi_backend.ai;

import java.util.List;

/** POST /api/v1/ai/final-checkpoints 응답 */
public record FinalCheckpointResponse(
        String checkpointId,
        String analysisId,
        String schoolId,
        List<CheckpointItem> items,   // 정확히 6개, 고정 순서
        String createdAt              // ISO 8601
) {}
