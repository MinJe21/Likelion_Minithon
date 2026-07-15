package org.example.dasi_backend.school;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/schools")
public class SchoolController {

    private final ClosedSchoolRepository repository;

    public SchoolController(ClosedSchoolRepository repository) {
        this.repository = repository;
    }

    /** 폐교 목록 (지역/학교급/활용현황/이름 필터 + 페이지네이션) */
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        List<ClosedSchool> filtered = repository.search(region, level, status, keyword);
        int total = filtered.size();
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);

        List<SchoolSummary> items = filtered.subList(from, to).stream()
                .map(SchoolSummary::of)
                .toList();

        return Map.of(
                "total", total,
                "page", page,
                "size", size,
                "items", items
        );
    }

    /** 폐교 상세 */
    @GetMapping("/{schoolId}")
    public ClosedSchool detail(@PathVariable String schoolId) {
        return repository.findById(schoolId)
                .orElseThrow(() -> new org.example.dasi_backend.common.NotFoundException("해당 폐교를 찾을 수 없습니다: " + schoolId));
    }

    /** 목록용 요약 DTO */
    public record SchoolSummary(String schoolId, String name, String region, String shortDescription) {
        static SchoolSummary of(ClosedSchool s) {
            return new SchoolSummary(s.schoolId(), s.name(), s.region(), s.shortDescription());
        }
    }
}
