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

    /** 아이디어의 필요 면적 성격 */
    public static AreaTier areaTier(String idea) {
        if (idea != null) {
            for (String kw : LARGE_KW) if (idea.contains(kw)) return AreaTier.LARGE;
            for (String kw : SMALL_KW) if (idea.contains(kw)) return AreaTier.SMALL;
        }
        return AreaTier.MEDIUM;
    }
}
