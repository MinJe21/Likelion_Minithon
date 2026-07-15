package org.example.dasi_backend.ai.scoring;

/**
 * 기준별 점수. score=null 이면 미확인(공개자료로 산정 불가).
 * weightedScore = (score/5) * weight, 미확인이면 0(단, 커버리지 계산에서 가중치 제외).
 */
public record CriterionScore(
        Integer score,        // 1~5 또는 null(미확인)
        int weight,           // 가중치(%)
        double weightedScore, // 가중 점수
        boolean confirmed,    // 확인 여부
        String reasonSeed     // 규칙/조사 근거(내부용). LLM 설명 생성 시 참고
) {
    public static CriterionScore confirmed(int score, int weight, String reasonSeed) {
        return new CriterionScore(score, weight, (score / 5.0) * weight, true, reasonSeed);
    }
    public static CriterionScore unknown(int weight, String reasonSeed) {
        return new CriterionScore(null, weight, 0.0, false, reasonSeed);
    }
}
