package org.example.dasi_backend.school;

import jakarta.annotation.PostConstruct;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * closed_schools.csv 를 시작 시 메모리에 적재하는 인메모리 저장소.
 * 전국 폐교 약 1,193건. MVP 범위에서는 DB 테이블 대신 메모리 조회로 충분하다.
 */
@Repository
public class ClosedSchoolRepository {

    private static final Logger log = LoggerFactory.getLogger(ClosedSchoolRepository.class);

    private final Map<String, ClosedSchool> byId = new LinkedHashMap<>();

    @PostConstruct
    void load() throws Exception {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .build();

        try (Reader reader = new InputStreamReader(
                new ClassPathResource("data/closed_schools.csv").getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {

            int i = 0;
            for (CSVRecord r : parser) {
                i++;
                String id = String.format("school_%04d", i);
                ClosedSchool s = new ClosedSchool(
                        id,
                        get(r, "폐교명"),
                        get(r, "시도명"),
                        get(r, "시군구명"),
                        get(r, "교육지원청명"),
                        toInt(get(r, "폐교연도")),
                        get(r, "학교급구분명"),
                        get(r, "활용현황구분명"),
                        toInt(get(r, "건물연면적")),
                        toInt(get(r, "대지")),
                        get(r, "소재지도로명주소"),
                        get(r, "소재지지번주소"),
                        get(r, "담당자 부서명"),
                        get(r, "담당자 전화번호")
                );
                byId.put(id, s);
            }
        }
        log.info("폐교 데이터 적재 완료: {}건", byId.size());
    }

    public List<ClosedSchool> findAll() {
        return new ArrayList<>(byId.values());
    }

    public Optional<ClosedSchool> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** 지역(시도/시군구)·학교급·활용현황·이름 키워드로 필터링 */
    public List<ClosedSchool> search(String region, String level, String status, String keyword) {
        return byId.values().stream()
                .filter(s -> region == null || region.isBlank()
                        || (s.sido() != null && s.sido().contains(region))
                        || (s.sigungu() != null && s.sigungu().contains(region)))
                .filter(s -> level == null || level.isBlank()
                        || (s.schoolLevel() != null && s.schoolLevel().equals(level)))
                .filter(s -> status == null || status.isBlank()
                        || (s.utilizationStatus() != null && s.utilizationStatus().equals(status)))
                .filter(s -> keyword == null || keyword.isBlank()
                        || (s.name() != null && s.name().contains(keyword)))
                .toList();
    }

    public int count() {
        return byId.size();
    }

    private static String get(CSVRecord r, String header) {
        try {
            return r.isMapped(header) ? r.get(header).trim() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Integer toInt(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            // "1,600" 같은 값 대비 콤마 제거
            return Integer.valueOf(v.replaceAll("[,\\s]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
