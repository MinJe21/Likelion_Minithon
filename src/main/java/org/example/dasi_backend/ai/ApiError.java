package org.example.dasi_backend.ai;

/** 모든 AI API 공통 오류 응답 */
public record ApiError(
        String code,
        String message,
        String requestId
) {}
