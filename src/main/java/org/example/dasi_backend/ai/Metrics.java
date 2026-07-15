package org.example.dasi_backend.ai;

/**
 * 아이디어 진단 5개 세부 지표. 각 지표는 {grade, reasons} 구조.
 * executionFeasibility 는 HIGH 일수록 실행 가능성이 높다는 의미(리스크 아님).
 */
public record Metrics(
        MetricDetail spaceSuitability,
        MetricDetail accessibility,
        MetricDetail regionalDemand,
        MetricDetail similarCaseSuitability,
        MetricDetail executionFeasibility
) {}
