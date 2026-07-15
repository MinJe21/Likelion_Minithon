package org.example.dasi_backend.ai;

/** POST /api/v1/ai/idea-evaluations 요청 바디 */
public record IdeaEvaluationRequest(SchoolInput school, String idea) {}
