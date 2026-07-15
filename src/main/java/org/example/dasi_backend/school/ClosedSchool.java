package org.example.dasi_backend.school;

/**
 * 전국 폐교 1건 (closed_schools.csv 의 한 행).
 * 수요 관련 필드는 데이터에 없고 스펙에서도 제외한다.
 */
public record ClosedSchool(
        String schoolId,          // 시스템 부여 ID (school_0001 ...)
        String name,              // 폐교명
        String sido,              // 시도명 (예: 경상북도)
        String sigungu,           // 시군구명 (예: 영천시)
        String eduOffice,         // 교육지원청명
        Integer closedYear,       // 폐교연도
        String schoolLevel,       // 학교급 (초등학교/중학교/고등학교)
        String utilizationStatus, // 활용현황 (자체활용/대부/미활용)
        Integer buildingArea,     // 건물연면적(㎡)
        Integer landArea,         // 대지면적(㎡)
        String roadAddress,       // 소재지 도로명주소
        String jibunAddress,      // 소재지 지번주소
        String department,        // 담당자 부서명
        String phone              // 담당자 전화번호
) {
    /** 지역 표기: "경상북도 영천시" */
    public String region() {
        return (sido == null ? "" : sido) + (sigungu == null || sigungu.isBlank() ? "" : " " + sigungu);
    }

    /** 목록용 한 줄 설명 */
    public String shortDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(schoolLevel == null ? "폐교" : schoolLevel);
        if (closedYear != null) sb.append(", ").append(closedYear).append("년 폐교");
        if (utilizationStatus != null && !utilizationStatus.isBlank()) sb.append(", 현재 ").append(utilizationStatus);
        if (buildingArea != null) sb.append(", 건물 ").append(buildingArea).append("㎡");
        return sb.toString();
    }
}
