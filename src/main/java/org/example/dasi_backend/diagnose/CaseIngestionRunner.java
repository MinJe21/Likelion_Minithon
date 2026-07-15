package org.example.dasi_backend.diagnose;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 시작 시 유사사례(case_library)를 pgvector 에 (재)적재한다.
 * 사례 수가 적으므로 매 기동마다 테이블을 비우고 새로 넣어 일관성을 보장한다.
 * 진단 시 이 벡터스토어에서 폐교/아이디어와 관련 높은 사례를 검색(RAG)한다.
 */
@Component
public class CaseIngestionRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CaseIngestionRunner.class);

    private final VectorStore vectorStore;
    private final CatalogService catalog;
    private final JdbcTemplate jdbcTemplate;

    public CaseIngestionRunner(VectorStore vectorStore, CatalogService catalog, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.catalog = catalog;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            jdbcTemplate.execute("DELETE FROM vector_store");
        } catch (Exception e) {
            log.info("vector_store 초기화 건너뜀(테이블 미생성일 수 있음): {}", e.getMessage());
        }

        List<Document> docs = catalog.cases().stream()
                .map(c -> Document.builder()
                        .text(c.toEmbeddingText())
                        .metadata(Map.of(
                                "case_id", c.caseId(),
                                "case_name", c.caseName()))
                        .build())
                .toList();

        if (docs.isEmpty()) {
            log.warn("적재할 유사사례가 없습니다.");
            return;
        }
        vectorStore.add(docs);
        log.info("유사사례 {}건 임베딩 후 pgvector 적재 완료", docs.size());
    }
}
