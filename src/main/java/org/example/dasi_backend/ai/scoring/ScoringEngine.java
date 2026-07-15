package org.example.dasi_backend.ai.scoring;

import org.example.dasi_backend.ai.SchoolInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * PDF 가중치 명세를 구현한 규칙 기반 스코어링 엔진.
 * 점수는 규칙/조사데이터/RAG로 결정하며 LLM은 관여하지 않는다.
 * 가중치: 공간30 · 접근성20 · 지역수요20 · 유사사례10 · 실행리스크20
 */
@Component
public class ScoringEngine {

    private static final Logger log = LoggerFactory.getLogger(ScoringEngine.class);
    private static final int W_SPACE = 30, W_ACCESS = 20, W_DEMAND = 20, W_SIMILAR = 10, W_RISK = 20;
    private static final int COVERAGE_MIN = 60;

    private final DemoSignalService demoSignals;
    private final VectorStore vectorStore;

    public ScoringEngine(DemoSignalService demoSignals, VectorStore vectorStore) {
        this.demoSignals = demoSignals;
        this.vectorStore = vectorStore;
    }

    public CoreScore score(SchoolInput school, String ideaOrModelContext) {
        // 1) 공간 적합성 — 면적 규칙
        CriterionScore space = scoreSpace(school);

        // 2) 접근성 / 3) 지역수요 — 데모 신호 있으면 확인, 없으면 미확인
        var sig = demoSignals.find(school.schoolId(), school.schoolName());
        CriterionScore access, demand;
        if (sig.isPresent() && sig.get().accessibility() != null) {
            var a = sig.get().accessibility();
            access = CriterionScore.confirmed(clamp(a.score()), W_ACCESS, a.reason());
        } else {
            access = CriterionScore.unknown(W_ACCESS, "공개자료로 접근성 확인 불가(진입도로·버스·거점 거리 미확인)");
        }
        if (sig.isPresent() && sig.get().regionalDemand() != null) {
            var d = sig.get().regionalDemand();
            demand = CriterionScore.confirmed(clamp(d.score()), W_DEMAND, d.reason());
        } else {
            demand = CriterionScore.unknown(W_DEMAND, "공개자료로 지역 수요 확인 불가(인구·관광·생활권 데이터 미확인)");
        }

        // 4) 유사사례 적합성 — RAG 벡터 유사도
        CriterionScore similar = scoreSimilarCase(ideaOrModelContext);

        // 5) 실행 리스크(5=리스크 낮음) — 데모 신호 있으면 확인, 없으면 미확인(우선 확인 대상)
        CriterionScore risk;
        if (sig.isPresent() && sig.get().executionRisk() != null) {
            var er = sig.get().executionRisk();
            risk = CriterionScore.confirmed(clamp(er.score()), W_RISK, er.reason());
        } else {
            risk = CriterionScore.unknown(W_RISK, "시설·행정·예산 상태 미확인 — 현장 점검 및 교육청 협의 필요");
        }

        // 종합 (확인된 기준만으로 정규화)
        List<CriterionScore> all = List.of(space, access, demand, similar, risk);
        int coverage = all.stream().filter(CriterionScore::confirmed).mapToInt(CriterionScore::weight).sum();
        double weightedSum = all.stream().filter(CriterionScore::confirmed).mapToDouble(CriterionScore::weightedScore).sum();

        Integer overall;
        String code, label;
        if (coverage < COVERAGE_MIN) {
            overall = null;
            code = "INSUFFICIENT_DATA";
            label = "현재 자료만으로 판단 제한";
        } else {
            overall = (int) Math.round(weightedSum / coverage * 100.0);
            if (overall >= 80) { code = "HIGH_BASIC_FIT"; label = "기초 활용 가능성이 높음"; }
            else if (overall >= 60) { code = "CONDITIONAL_REVIEW"; label = "일부 조건 확인 후 검토 가능"; }
            else if (overall >= 40) { code = "MAJOR_CHECKS_REQUIRED"; label = "주요 조건 보완 필요"; }
            else { code = "LIMITED_FIT"; label = "현재 확인된 조건상 검토 부담이 큼"; }
        }

        log.info("스코어링: school={}, overall={}, coverage={}%, status={}",
                school.schoolId(), overall, coverage, code);
        return new CoreScore(overall, coverage, code, label, space, access, demand, similar, risk);
    }

    private CriterionScore scoreSpace(SchoolInput s) {
        Double site = s.siteArea();
        Double bld = s.buildingArea();
        if (site != null) {
            int sc = site >= 20000 ? 5 : site >= 12000 ? 4 : site >= 8000 ? 3 : site >= 4000 ? 2 : 1;
            return CriterionScore.confirmed(sc, W_SPACE, "부지면적 " + fmt(site) + "㎡ 기준");
        }
        if (bld != null) {
            int sc = bld >= 3000 ? 5 : bld >= 1800 ? 4 : bld >= 900 ? 3 : bld >= 400 ? 2 : 1;
            return CriterionScore.confirmed(sc, W_SPACE, "건물면적 " + fmt(bld) + "㎡ 기준");
        }
        return CriterionScore.unknown(W_SPACE, "면적 정보 미확인");
    }

    private CriterionScore scoreSimilarCase(String query) {
        try {
            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder().query(query == null ? "폐교 활용" : query).topK(5).build());
            if (docs == null || docs.isEmpty()) {
                return CriterionScore.unknown(W_SIMILAR, "참고할 유사사례를 찾지 못함");
            }
            Double top = docs.get(0).getScore();
            int sc;
            if (top == null) sc = 3;
            else if (top >= 0.72) sc = 5;
            else if (top >= 0.62) sc = 4;
            else if (top >= 0.52) sc = 3;
            else if (top >= 0.42) sc = 2;
            else sc = 1;
            String names = docs.stream().limit(2)
                    .map(d -> String.valueOf(d.getMetadata().getOrDefault("case_name", "사례")))
                    .reduce((a, b) -> a + ", " + b).orElse("");
            return CriterionScore.confirmed(sc, W_SIMILAR, "유사사례 검색 결과 참고(" + names + ")");
        } catch (Exception e) {
            log.warn("유사사례 스코어링 실패: {}", e.getMessage());
            return CriterionScore.unknown(W_SIMILAR, "유사사례 검색 실패");
        }
    }

    private static int clamp(int v) {
        return Math.max(1, Math.min(5, v));
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.format("%,d", (long) d) : String.valueOf(d);
    }
}
