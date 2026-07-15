package org.example.dasi_backend.ai;

import java.util.List;

/**
 * 프론트가 request 로 전달하는 폐교 정보 (수정 명세 기준).
 */
public record SchoolInput(
        String schoolId,
        String schoolName,
        String address,
        String closedDate,          // YYYY-MM-DD, nullable
        Double buildingArea,        // 건물 연면적(㎡), 소수 허용
        Double siteArea,            // 전체 부지 면적(㎡), 소수 허용
        String utilizationPlan,     // 현재 활용 상태 또는 계획 (예: 미활용)
        String promotionPlan,       // 교육청/지자체 추진 계획 (예: 대부 또는 매각 검토)
        List<String> nearbyResources
) {
    /** LLM 프롬프트/임베딩 검색에 넣을 텍스트 요약 */
    public String toPromptText() {
        String resources = (nearbyResources == null || nearbyResources.isEmpty())
                ? "확인되지 않음" : String.join(", ", nearbyResources);
        return String.join("\n",
                "학교명: " + safe(schoolName),
                "소재지: " + safe(address),
                "폐교일: " + (closedDate == null || closedDate.isBlank() ? "미상" : closedDate),
                "건물면적(㎡): " + (buildingArea == null ? "미상" : trim(buildingArea)),
                "부지면적(㎡): " + (siteArea == null ? "미상" : trim(siteArea)),
                "현재 활용상태/계획: " + safe(utilizationPlan),
                "추진계획: " + safe(promotionPlan),
                "주변자원: " + resources
        );
    }

    private static String trim(Double d) {
        if (d == Math.floor(d)) return String.valueOf(d.longValue());
        return String.valueOf(d);
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "미상" : s;
    }
}
