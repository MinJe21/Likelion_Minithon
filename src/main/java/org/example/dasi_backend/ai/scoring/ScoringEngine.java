package org.example.dasi_backend.ai.scoring;

import org.example.dasi_backend.ai.SchoolInput;
import org.example.dasi_backend.diagnose.CatalogService;
import org.example.dasi_backend.diagnose.SimilarCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

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
    private final CatalogService catalog;

    public ScoringEngine(DemoSignalService demoSignals, VectorStore vectorStore, CatalogService catalog) {
        this.demoSignals = demoSignals;
        this.vectorStore = vectorStore;
        this.catalog = catalog;
    }

    public CoreScore score(SchoolInput school, String idea) {
        // 1) 공간 적합성 — 아이디어의 필요 면적 성격 + 학교 면적
        CriterionScore space = scoreSpace(school, idea);

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

        // 4) 유사사례 적합성 — 아이디어 용도와 실제 사례의 활용모델 매칭 + 벡터 유사도
        String ragQuery = (idea == null ? "" : idea) + " " + (school.address() == null ? "" : school.address());
        CriterionScore similar = scoreSimilarCase(idea, ragQuery);

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

    /** 아이디어의 필요 면적 성격(LARGE/MEDIUM/SMALL)에 맞춰 학교 면적으로 채점 */
    private CriterionScore scoreSpace(SchoolInput s, String idea) {
        Double site = s.siteArea();
        Double bld = s.buildingArea();
        IdeaClassifier.AreaTier tier = IdeaClassifier.areaTier(idea);

        switch (tier) {
            case LARGE -> {
                // 캠핑·스포츠·리조트 등 넓은 부지 필요 → 부지면적 기준(엄격)
                if (site != null) {
                    int sc = site >= 25000 ? 5 : site >= 15000 ? 4 : site >= 9000 ? 3 : site >= 5000 ? 2 : 1;
                    return CriterionScore.confirmed(sc, W_SPACE, "넓은 부지 필요 유형 · 부지 " + fmt(site) + "㎡ 기준");
                }
                return bld != null
                        ? CriterionScore.confirmed(2, W_SPACE, "넓은 부지 필요하나 부지면적 미확인, 건물만 확인")
                        : CriterionScore.unknown(W_SPACE, "면적 정보 미확인");
            }
            case SMALL -> {
                // 카페·공방·전시 등 실내 소규모 → 건물면적 기준(관대)
                if (bld != null) {
                    int sc = bld >= 1500 ? 5 : bld >= 800 ? 4 : bld >= 400 ? 3 : bld >= 200 ? 2 : 1;
                    return CriterionScore.confirmed(sc, W_SPACE, "실내 소규모 유형 · 건물 " + fmt(bld) + "㎡ 기준");
                }
                return site != null
                        ? CriterionScore.confirmed(4, W_SPACE, "소규모 유형 · 부지 여유(" + fmt(site) + "㎡)")
                        : CriterionScore.unknown(W_SPACE, "면적 정보 미확인");
            }
            default -> {
                // 체험센터·커뮤니티 등 중간 → 건물면적 중심
                if (bld != null) {
                    int sc = bld >= 2500 ? 5 : bld >= 1500 ? 4 : bld >= 800 ? 3 : bld >= 400 ? 2 : 1;
                    return CriterionScore.confirmed(sc, W_SPACE, "중규모 유형 · 건물 " + fmt(bld) + "㎡ 기준");
                }
                if (site != null) {
                    int sc = site >= 15000 ? 4 : site >= 8000 ? 3 : 2;
                    return CriterionScore.confirmed(sc, W_SPACE, "중규모 유형 · 부지 " + fmt(site) + "㎡ 기준");
                }
                return CriterionScore.unknown(W_SPACE, "면적 정보 미확인");
            }
        }
    }

    /** 아이디어 용도와 실제 사례의 활용모델 매칭 + 벡터 유사도로 채점 */
    private CriterionScore scoreSimilarCase(String idea, String ragQuery) {
        try {
            Set<String> ideaModels = IdeaClassifier.models(idea);
            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder().query(ragQuery == null || ragQuery.isBlank() ? "폐교 활용" : ragQuery)
                            .topK(5).build());
            if (docs == null || docs.isEmpty()) {
                return CriterionScore.unknown(W_SIMILAR, "참고할 유사사례를 찾지 못함");
            }
            Double top = docs.get(0).getScore();
            boolean matchTop3 = matchesModel(docs, 3, ideaModels);
            boolean matchAny = matchesModel(docs, 5, ideaModels);

            int sc;
            String note;
            if (ideaModels.isEmpty()) {
                // 정형화되지 않은 생소한 아이디어 → 참고할 공식 사례가 적음
                sc = (top != null && top >= 0.6) ? 3 : 2;
                note = "정형화되지 않은 용도로 직접 대응되는 활용사례가 제한적";
            } else if (matchTop3) {
                sc = (top != null && top >= 0.65) ? 5 : 4;
                note = "동일 용도(" + String.join(",", ideaModels) + ")의 유사사례가 상위에 존재";
            } else if (matchAny) {
                sc = 3;
                note = "관련 용도의 유사사례가 일부 확인됨";
            } else {
                sc = 2;
                note = "동일 용도의 참고 사례가 부족함";
            }
            String names = docs.stream().limit(2)
                    .map(d -> String.valueOf(d.getMetadata().getOrDefault("case_name", "사례")))
                    .reduce((a, b) -> a + ", " + b).orElse("");
            return CriterionScore.confirmed(sc, W_SIMILAR, note + " (상위: " + names + ")");
        } catch (Exception e) {
            log.warn("유사사례 스코어링 실패: {}", e.getMessage());
            return CriterionScore.unknown(W_SIMILAR, "유사사례 검색 실패");
        }
    }

    /** 상위 n개 사례 중 아이디어 용도와 활용모델이 겹치는 사례가 있는지 */
    private boolean matchesModel(List<Document> docs, int n, Set<String> ideaModels) {
        if (ideaModels.isEmpty()) return false;
        return docs.stream().limit(n)
                .map(d -> catalog.caseById(String.valueOf(d.getMetadata().get("case_id"))).orElse(null))
                .filter(c -> c != null && c.relatedModels() != null)
                .anyMatch(c -> c.relatedModels().stream().anyMatch(ideaModels::contains));
    }

    private static int clamp(int v) {
        return Math.max(1, Math.min(5, v));
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.format("%,d", (long) d) : String.valueOf(d);
    }
}
