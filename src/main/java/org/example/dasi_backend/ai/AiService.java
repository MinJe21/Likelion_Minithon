package org.example.dasi_backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dasi_backend.ai.llm.LlmCheckpoints;
import org.example.dasi_backend.ai.llm.LlmIdeaExplanation;
import org.example.dasi_backend.ai.llm.LlmRecommendations;
import org.example.dasi_backend.ai.scoring.CoreScore;
import org.example.dasi_backend.ai.scoring.CriterionScore;
import org.example.dasi_backend.ai.scoring.ScoringEngine;
import org.example.dasi_backend.diagnose.CatalogService;
import org.example.dasi_backend.diagnose.SimilarCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 프론트 수정 명세(message)에 맞춘 AI 진단·체크포인트 서비스.
 * - 폐교 정보는 요청으로 전달받음(SchoolInput)
 * - 유사사례는 pgvector에서 RAG 검색해 프롬프트에 주입
 * - LLM 구조화 출력 → 서버가 id/createdAt 부여 및 검증
 * - 등급은 Grade 영어 코드, 사용자 노출 텍스트는 한국어
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int IDEA_MIN = 10;
    private static final int IDEA_MAX = 1000;

    private static final String SYSTEM_PROMPT = """
            너는 폐교 활용 가능성을 진단하는 AI 분석가다.
            제공된 폐교 데이터(면적, 소재지, 추진계획, 주변자원)와 유사사례를 근거로 판단한다.

            규칙:
            1. 실제 사업 성공/법률/인허가 가능 여부를 단정하지 말 것("확인 필요" 형태로 표현).
            2. 제공된 데이터와 사례 근거 안에서 판단할 것. 부족하면 낮은 등급과 확인 필요로 표현.
            3. 모든 등급 값은 반드시 VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH 중 하나(영어 코드)로만 출력.
            4. 사용자가 읽는 설명/이유/장점/단점/모델명 등 텍스트는 모두 한국어로 작성.
            5. executionFeasibility 는 값이 높을수록(HIGH) 실행 가능성이 높다는 의미다.
            6. 다음을 확인된 사실처럼 지어내지 말 것: 주민 동의 비율, 실제 리모델링 비용, 건물 안전등급,
               전기/소방/수도 상태, 실제 이용자 수, 대부/매각 확정 여부, 근거 없는 법령·조례명.
               확인되지 않은 내용은 "확인이 필요합니다 / 사전 협의가 필요합니다 / 현장 점검이 필요합니다 /
               담당 기관에 문의해야 합니다 / 수요조사를 통해 검증해야 합니다" 형태로 표현.
            금지 표현: "성공합니다", "법적으로 가능합니다", "수익성이 보장됩니다".
            """;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final CatalogService catalog;
    private final ScoringEngine scoringEngine;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiService(ChatClient.Builder builder, VectorStore vectorStore, CatalogService catalog,
                     ScoringEngine scoringEngine) {
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
        this.vectorStore = vectorStore;
        this.catalog = catalog;
        this.scoringEngine = scoringEngine;
    }

    // ---------------------------------------------------------------- 활용모델 추천

    public RecommendationResponse recommend(RecommendationRequest request) {
        SchoolInput school = requireSchool(request == null ? null : request.school());

        String prompt = """
                다음 폐교에 적합한 활용모델을 정확히 3개 추천해줘.

                [폐교 데이터]
                %s

                [활용모델 후보]
                %s

                [참고 유사사례]
                %s

                각 추천은 title, description, strengths(2개), risks(2개)를 포함하고,
                학교의 공간(부지/건물 면적), 소재지, 추진계획, 주변자원과 연결해서 작성해줘.
                반드시 서로 다른 3개의 활용모델을 제시해줘.
                """.formatted(school.toPromptText(), toJson(catalog.useModels()),
                toJson(retrieveCases(school.toPromptText())));

        LlmRecommendations llm = callLlm(() ->
                chatClient.prompt().user(prompt).call().entity(LlmRecommendations.class));

        if (llm == null || llm.recommendations() == null || llm.recommendations().size() < 3) {
            throw new AiException(AiException.Code.AI_RESPONSE_INVALID, "AI가 추천 3개를 생성하지 못했습니다.");
        }

        List<RecommendationItem> items = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            var it = llm.recommendations().get(i);
            items.add(new RecommendationItem(
                    String.format("recommendation_%02d", i + 1),
                    i + 1, it.title(), it.description(), it.strengths(), it.risks()));
        }
        log.info("추천 완료: school={}, {}건", school.schoolId(), items.size());
        return new RecommendationResponse(newAnalysisId(), school.schoolId(), items, now());
    }

    // ---------------------------------------------------------------- 아이디어 진단

    public IdeaEvaluationResponse evaluateIdea(IdeaEvaluationRequest request) {
        SchoolInput school = requireSchool(request == null ? null : request.school());
        String idea = request == null ? null : request.idea();
        if (idea == null || idea.isBlank()) {
            throw new AiException(AiException.Code.IDEA_REQUIRED, "아이디어를 입력해야 합니다.");
        }
        String trimmed = idea.trim();
        if (trimmed.length() < IDEA_MIN) {
            throw new AiException(AiException.Code.IDEA_TOO_SHORT, "아이디어는 최소 " + IDEA_MIN + "자 이상 입력해야 합니다.");
        }
        if (trimmed.length() > IDEA_MAX) {
            throw new AiException(AiException.Code.IDEA_TOO_LONG, "아이디어는 최대 " + IDEA_MAX + "자까지 입력할 수 있습니다.");
        }

        // 1) 규칙 엔진이 점수를 결정 (LLM 아님)
        CoreScore cs = scoringEngine.score(school, trimmed);

        // 2) LLM은 계산된 점수/등급을 "설명"만 한다
        String prompt = """
                아래 폐교와 아이디어에 대해 규칙 엔진이 이미 계산한 등급/점수를 설명하는 문장을 작성해줘.
                점수를 바꾸지 말고, 주어진 등급과 일관된 판단 이유를 써라. 미확인 항목은 무엇을 확인해야 하는지 설명해라.

                [폐교 데이터]
                %s

                [사용자 아이디어]
                %s

                [규칙 엔진 계산 결과]
                %s

                [참고 유사사례]
                %s

                작성할 것:
                - summary: 종합 등급/점수와 일관된 한 문장 요약
                - reasons: 5개 지표(spaceSuitability, accessibility, regionalDemand, similarCaseSuitability, executionFeasibility)
                  각각 2~3개의 판단 이유
                - strengths(2), weaknesses(2), alternativeModels(2), recommendation
                """.formatted(school.toPromptText(), trimmed, describeScores(cs),
                toJson(retrieveCases(trimmed + " / " + school.toPromptText())));

        LlmIdeaExplanation ex = callLlm(() ->
                chatClient.prompt().user(prompt).call().entity(LlmIdeaExplanation.class));
        if (ex == null || ex.summary() == null || ex.summary().isBlank() || ex.reasons() == null) {
            throw new AiException(AiException.Code.AI_RESPONSE_INVALID, "AI 진단 설명 형식을 확인할 수 없습니다.");
        }

        // 3) 규칙 점수 + LLM 설명 결합
        LlmIdeaExplanation.Reasons r = ex.reasons();
        Metrics metrics = new Metrics(
                metric(cs.spaceFit(), r.spaceSuitability()),
                metric(cs.accessibility(), r.accessibility()),
                metric(cs.regionalDemand(), r.regionalDemand()),
                metric(cs.similarCaseFit(), r.similarCaseSuitability()),
                metric(cs.executionRisk(), r.executionFeasibility()));

        log.info("아이디어 진단 완료: school={}, overall={}, status={}",
                school.schoolId(), cs.overallScore(), cs.statusCode());
        return new IdeaEvaluationResponse(
                newEvaluationId(), school.schoolId(), trimmed,
                bandToGrade(cs.overallScore()), cs.overallScore(), cs.coverage(),
                cs.statusCode(), cs.statusLabel(),
                ex.summary(), metrics,
                ex.strengths(), ex.weaknesses(), ex.alternativeModels(), ex.recommendation(), now());
    }

    /** 규칙 점수 1건 + LLM 설명을 MetricDetail 로 결합 */
    private MetricDetail metric(CriterionScore c, List<String> reasons) {
        List<String> rs = (reasons == null || reasons.isEmpty())
                ? List.of(c.reasonSeed() == null ? "확인이 필요합니다." : c.reasonSeed())
                : reasons;
        return new MetricDetail(scoreToGrade(c.score()), c.score(), c.weight(), c.weightedScore(), rs);
    }

    /** LLM 프롬프트에 넣을, 규칙 엔진 계산 결과 서술 */
    private String describeScores(CoreScore cs) {
        StringBuilder sb = new StringBuilder();
        sb.append(line("공간 적합성", cs.spaceFit()));
        sb.append(line("접근성", cs.accessibility()));
        sb.append(line("지역 수요", cs.regionalDemand()));
        sb.append(line("유사사례 적합성", cs.similarCaseFit()));
        sb.append(line("실행 가능성(리스크)", cs.executionRisk()));
        sb.append("종합점수: ").append(cs.overallScore() == null ? "판단 제한" : cs.overallScore() + "점")
                .append(" / 커버리지: ").append(cs.coverage()).append("% / 상태: ").append(cs.statusLabel());
        return sb.toString();
    }

    private String line(String name, CriterionScore c) {
        String grade = c.score() == null ? "미확인" : c.score() + "/5(" + scoreToGrade(c.score()) + ")";
        return "- " + name + ": " + grade + " — 근거: " + (c.reasonSeed() == null ? "" : c.reasonSeed()) + "\n";
    }

    private static Grade scoreToGrade(Integer score) {
        if (score == null) return null;
        return switch (score) {
            case 5 -> Grade.VERY_HIGH;
            case 4 -> Grade.HIGH;
            case 3 -> Grade.MEDIUM;
            case 2 -> Grade.LOW;
            default -> Grade.VERY_LOW;
        };
    }

    private static Grade bandToGrade(Integer overall) {
        if (overall == null) return null;
        if (overall >= 80) return Grade.VERY_HIGH;
        if (overall >= 60) return Grade.HIGH;
        if (overall >= 40) return Grade.MEDIUM;
        if (overall >= 20) return Grade.LOW;
        return Grade.VERY_LOW;
    }

    // ---------------------------------------------------------------- 최종 체크포인트

    public FinalCheckpointResponse generateCheckpoints(FinalCheckpointRequest request) {
        if (request == null || request.sourceType() == null) {
            throw new AiException(AiException.Code.INVALID_SOURCE_TYPE, "sourceType 이 필요합니다.");
        }
        String sourceType = request.sourceType().trim();
        boolean recommendation = "RECOMMENDATION".equals(sourceType);
        boolean ideaEval = "IDEA_EVALUATION".equals(sourceType);
        if (!recommendation && !ideaEval) {
            throw new AiException(AiException.Code.INVALID_SOURCE_TYPE, "지원하지 않는 sourceType 입니다: " + sourceType);
        }
        SchoolInput school = requireSchool(request.school());

        String context;
        if (recommendation) {
            SelectedRecommendation r = request.selectedRecommendation();
            if (r == null || r.title() == null || r.title().isBlank()) {
                throw new AiException(AiException.Code.SELECTED_RECOMMENDATION_REQUIRED,
                        "추천 경로에서는 selectedRecommendation 이 필요합니다.");
            }
            context = "[선택한 추천 활용모델]\n" + toJson(r);
        } else {
            IdeaEvaluationInput e = request.ideaEvaluation();
            if (e == null || e.idea() == null || e.idea().isBlank()) {
                throw new AiException(AiException.Code.IDEA_EVALUATION_REQUIRED,
                        "직접 작성 경로에서는 ideaEvaluation 이 필요합니다.");
            }
            context = "[사용자 아이디어 진단 결과]\n" + toJson(e);
        }

        String prompt = """
                아래 폐교와 선택 결과를 바탕으로, 실제 사업 검토 시 확인해야 할 최종 체크포인트를
                6개 카테고리 각각에 대해 정확히 1개씩(총 6개) 작성해줘.

                [폐교 데이터]
                %s

                %s

                각 카테고리(ADMINISTRATION 행정, FACILITY 시설, BUDGET 예산, DEMAND 수요,
                OPERATING_SUSTAINABILITY 운영 지속성, COMMUNITY_ACCEPTANCE 지역수용성)에 대해
                - category: 위 6개 코드 중 하나
                - priority: HIGH/MEDIUM/LOW (학교·활용모델 상황에 따라 다르게 부여)
                - note: 이 학교와 활용모델에 맞춘 확인 문장(한국어)
                - tip: 실무에서 놓치지 말아야 할 참고 팁(한국어)
                를 작성해줘. 6개 카테고리를 모두 포함하고, 서로 다른 문장을 쓰며,
                확인되지 않은 수치·법령·안전등급 등을 지어내지 말고 "확인 필요" 형태로 표현해줘.
                """.formatted(school.toPromptText(), context);

        LlmCheckpoints llm = callLlm(() ->
                chatClient.prompt().user(prompt).call().entity(LlmCheckpoints.class));

        List<CheckpointItem> ordered = validateAndOrderCheckpoints(llm);

        log.info("체크포인트 생성 완료: school={}, source={}", school.schoolId(), sourceType);
        return new FinalCheckpointResponse(
                newCheckpointId(), request.analysisId(), school.schoolId(), ordered, now());
    }

    /** LLM 체크포인트를 검증하고 고정 순서(6개)로 재배열한다. */
    private List<CheckpointItem> validateAndOrderCheckpoints(LlmCheckpoints llm) {
        if (llm == null || llm.items() == null) {
            throw new AiException(AiException.Code.CHECKPOINT_RESPONSE_INVALID, "체크포인트 결과가 비어 있습니다.");
        }
        Map<CheckpointCategory, CheckpointItem> byCat = new LinkedHashMap<>();
        for (CheckpointItem it : llm.items()) {
            if (it == null || it.category() == null || it.priority() == null
                    || it.note() == null || it.note().isBlank()
                    || it.tip() == null || it.tip().isBlank()) {
                continue;
            }
            byCat.putIfAbsent(it.category(), it);
        }
        List<CheckpointItem> ordered = new ArrayList<>();
        for (CheckpointCategory cat : CheckpointCategory.values()) {
            CheckpointItem it = byCat.get(cat);
            if (it == null) {
                throw new AiException(AiException.Code.CHECKPOINT_RESPONSE_INVALID,
                        "최종 체크포인트 결과 형식을 확인할 수 없습니다.");
            }
            // 카테고리 순서를 명세 순서로 고정
            ordered.add(new CheckpointItem(cat, it.priority(), it.note(), it.tip()));
        }
        return ordered;
    }

    // ---------------------------------------------------------------- 공통

    private SchoolInput requireSchool(SchoolInput school) {
        if (school == null) {
            throw new AiException(AiException.Code.SCHOOL_DATA_MISSING, "school 정보가 필요합니다.");
        }
        if (school.schoolId() == null || school.schoolId().isBlank()
                || school.schoolName() == null || school.schoolName().isBlank()) {
            throw new AiException(AiException.Code.SCHOOL_DATA_MISSING, "schoolId 와 schoolName 은 필수입니다.");
        }
        return school;
    }

    /** RAG: 쿼리와 관련 높은 유사사례 검색 */
    private List<SimilarCase> retrieveCases(String query) {
        try {
            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder().query(query).topK(8).build());
            if (docs == null || docs.isEmpty()) return catalog.cases();
            return docs.stream()
                    .map(d -> String.valueOf(d.getMetadata().get("case_id")))
                    .map(id -> catalog.caseById(id).orElse(null))
                    .filter(c -> c != null)
                    .toList();
        } catch (Exception e) {
            log.warn("유사사례 검색 실패, 전체 사례로 대체: {}", e.getMessage());
            return catalog.cases();
        }
    }

    private <T> T callLlm(java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            String msg = String.valueOf(e.getMessage()).toLowerCase();
            if (msg.contains("timeout") || msg.contains("timed out")) {
                throw new AiException(AiException.Code.AI_REQUEST_TIMEOUT, "AI 요청이 시간 초과되었습니다.", e);
            }
            if (msg.contains("429") || msg.contains("rate") || msg.contains("quota")) {
                throw new AiException(AiException.Code.RATE_LIMIT_EXCEEDED, "AI 요청 한도를 초과했습니다.", e);
            }
            log.error("AI 응답 처리 실패", e);
            throw new AiException(AiException.Code.AI_RESPONSE_FAILED, "AI 분석 결과를 생성하지 못했습니다.", e);
        }
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    private static String newAnalysisId() {
        return "analysis-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String newEvaluationId() {
        return "eval-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String newCheckpointId() {
        return "checkpoint-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String now() {
        return OffsetDateTime.now(SEOUL).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
