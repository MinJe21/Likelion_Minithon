package org.example.dasi_backend.ai.scoring;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 사용자 아이디어(자유문장)를 활용모델 카테고리와 필요 면적 성격으로 분류한다.
 * 규칙 스코어링이 아이디어에 따라 달라지도록 하기 위한 키워드 기반 휴리스틱.
 */
public final class IdeaClassifier {

    public enum AreaTier { LARGE, MEDIUM, SMALL }

    /** 용도 적합성: 폐교 재생 취지·행정 여건 부합도 */
    public enum Appropriateness { FIT, WEAK, UNFIT }

    // 부적합/유해·행정 불허 가능성이 큰 용도(혐오시설 포함)
    private static final List<String> UNFIT_KW = List.of(
            "쓰레기", "매립", "소각", "폐기물", "하수", "분뇨", "축사", "공장", "제조",
            "유흥", "도박", "카지노", "성인", "주점", "나이트", "클럽",
            "화장장", "장례", "납골", "교도소", "사격", "무기", "정유", "발전소");
    // 폐교 특성을 특별히 살리지 못하는 일반 상업 용도
    private static final List<String> WEAK_KW = List.of(
            "피시방", "피씨방", "PC방", "pc방", "노래방", "당구", "오락실", "편의점",
            "사무실", "오피스", "창고", "물류", "주차장", "모텔", "은행", "약국");

    // 활용모델 카테고리별 키워드
    private static final Map<String, List<String>> MODEL_KEYWORDS = Map.of(
            "유아 체험센터", List.of("유아", "어린이", "아동", "키즈", "놀이", "체험학습"),
            "지역 카페·문화공간", List.of("카페", "커피", "북카페", "문화공간", "전시", "갤러리"),
            "폐교 숙박·펜션", List.of("숙박", "펜션", "게스트하우스", "민박", "호텔", "리조트"),
            "창작·예술 레지던시", List.of("예술", "창작", "작가", "레지던시", "미술", "공방", "스튜디오"),
            "주민 커뮤니티·복합문화공간", List.of("주민", "커뮤니티", "복지", "도서관", "평생교육", "공동체"),
            "캠핑·야외 체험장", List.of("캠핑", "글램핑", "야외", "스포츠", "운동", "체육", "농장", "농촌체험")
    );

    // 넓은 부지가 필요한(LARGE) / 실내 소규모(SMALL) 키워드
    private static final List<String> LARGE_KW = List.of(
            "캠핑", "글램핑", "야외", "스포츠", "운동", "체육", "농장", "리조트", "주차", "대규모", "단지", "골프");
    private static final List<String> SMALL_KW = List.of(
            "카페", "북카페", "공방", "전시", "갤러리", "스튜디오", "사무", "소규모", "작은", "서점");

    private IdeaClassifier() {}

    /** 아이디어에 해당하는 활용모델 카테고리 집합(없으면 빈 집합 = 정형화되지 않은 아이디어) */
    public static Set<String> models(String idea) {
        Set<String> out = new LinkedHashSet<>();
        if (idea == null) return out;
        for (var e : MODEL_KEYWORDS.entrySet()) {
            for (String kw : e.getValue()) {
                if (idea.contains(kw)) { out.add(e.getKey()); break; }
            }
        }
        return out;
    }

    /**
     * 용도 적합성 판정.
     * - UNFIT: 혐오·유해·행정 불허 가능성이 큰 용도
     * - FIT: 폐교 재생에 부합하는 활용모델(교육·문화·카페·숙박·예술·커뮤니티·캠핑)에 매칭
     * - WEAK: 그 외(일반 상업 또는 정형화되지 않은 아이디어) → 보수적으로 중간 이하
     */
    public static Appropriateness appropriateness(String idea) {
        if (idea == null || idea.isBlank()) return Appropriateness.WEAK;
        for (String kw : UNFIT_KW) if (idea.contains(kw)) return Appropriateness.UNFIT;
        if (!models(idea).isEmpty()) return Appropriateness.FIT;
        for (String kw : WEAK_KW) if (idea.contains(kw)) return Appropriateness.WEAK;
        return Appropriateness.WEAK;
    }

    /** 아이디어의 필요 면적 성격 */
    public static AreaTier areaTier(String idea) {
        if (idea != null) {
            for (String kw : LARGE_KW) if (idea.contains(kw)) return AreaTier.LARGE;
            for (String kw : SMALL_KW) if (idea.contains(kw)) return AreaTier.SMALL;
        }
        return AreaTier.MEDIUM;
    }
}
