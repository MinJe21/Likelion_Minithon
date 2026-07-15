package org.example.dasi_backend.diagnose;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 활용모델·유사사례 카탈로그를 시작 시 메모리에 적재한다. */
@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private List<UseModel> useModels = List.of();
    private List<SimilarCase> cases = List.of();
    private Map<String, SimilarCase> casesById = Map.of();

    @PostConstruct
    void load() throws Exception {
        useModels = objectMapper.readValue(
                new ClassPathResource("data/use_models.json").getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, UseModel.class));

        // data/cases/ 아래 모든 *.json 을 유사사례로 로드 (curated + 실제 활용현황 + 향후 추가분)
        List<SimilarCase> all = new ArrayList<>();
        Resource[] caseFiles = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:data/cases/*.json");
        for (Resource r : caseFiles) {
            List<SimilarCase> part = objectMapper.readValue(r.getInputStream(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, SimilarCase.class));
            all.addAll(part);
            log.info("  사례 파일 로드: {} ({}건)", r.getFilename(), part.size());
        }
        cases = all;
        casesById = cases.stream()
                .collect(Collectors.toMap(SimilarCase::caseId, Function.identity(), (a, b) -> a));
        log.info("카탈로그 적재 완료: 활용모델 {}개, 유사사례 {}개", useModels.size(), cases.size());
    }

    public List<UseModel> useModels() {
        return useModels;
    }

    public List<SimilarCase> cases() {
        return cases;
    }

    public Optional<SimilarCase> caseById(String id) {
        return Optional.ofNullable(casesById.get(id));
    }
}
