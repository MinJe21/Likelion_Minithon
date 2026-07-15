package org.example.dasi_backend.ai.scoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 데모용 폐교의 접근성·지역수요 사전 점수(1~5)를 로드한다.
 * demo_school_signals.json 에 있는 학교는 이 점수를 "확인된 값"으로 사용하고,
 * 그 외 학교는 접근성·수요를 null(미확인)로 둔다.
 */
@Service
public class DemoSignalService {

    private static final Logger log = LoggerFactory.getLogger(DemoSignalService.class);
    private final ObjectMapper mapper = new ObjectMapper();

    /** key: schoolId 또는 schoolName */
    private final Map<String, Signal> byKey = new LinkedHashMap<>();

    public record Metric(int score, String reason, String evidence) {}
    public record Signal(String schoolName, Metric accessibility, Metric regionalDemand, Metric executionRisk) {}

    @PostConstruct
    void load() throws Exception {
        var res = new ClassPathResource("data/demo_school_signals.json");
        if (!res.exists()) {
            log.info("demo_school_signals.json 없음 — 데모 신호 비활성");
            return;
        }
        JsonNode root = mapper.readTree(res.getInputStream());
        JsonNode signals = root.path("signals");
        signals.fields().forEachRemaining(e -> {
            String id = e.getKey();
            JsonNode n = e.getValue();
            String name = n.path("schoolName").asText(null);
            Signal s = new Signal(name, metric(n.path("accessibility")),
                    metric(n.path("regionalDemand")), metric(n.path("executionRisk")));
            byKey.put(id, s);
            if (name != null) byKey.put(name, s);   // 이름으로도 조회 가능
        });
        log.info("데모 신호 적재 완료: {}개 학교", signals.size());
    }

    private Metric metric(JsonNode n) {
        if (n == null || n.isMissingNode()) return null;
        return new Metric(n.path("score").asInt(0), n.path("reason").asText(""), n.path("evidence").asText(""));
    }

    /** schoolId 우선, 없으면 schoolName 으로 조회 */
    public Optional<Signal> find(String schoolId, String schoolName) {
        if (schoolId != null && byKey.containsKey(schoolId)) return Optional.of(byKey.get(schoolId));
        if (schoolName != null && byKey.containsKey(schoolName)) return Optional.of(byKey.get(schoolName));
        return Optional.empty();
    }
}
