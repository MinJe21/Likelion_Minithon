package org.example.dasi_backend.diagnose;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** 유사사례 (data/cases/*.json). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SimilarCase(
        @JsonProperty("case_id") String caseId,
        @JsonProperty("case_name") String caseName,
        @JsonProperty("region") String region,
        @JsonProperty("related_models") List<String> relatedModels,
        @JsonProperty("summary") String summary,
        @JsonProperty("positive_insight") String positiveInsight,
        @JsonProperty("risk_insight") String riskInsight,
        @JsonProperty("source") String source
) {
    /** 임베딩·검색용 텍스트 */
    public String toEmbeddingText() {
        return String.join("\n",
                "사례명: " + nz(caseName),
                (region == null || region.isBlank() ? "" : "지역: " + region),
                "요약: " + nz(summary),
                "긍정 포인트: " + nz(positiveInsight),
                "리스크 포인트: " + nz(riskInsight),
                "관련 활용모델: " + (relatedModels == null ? "" : String.join(", ", relatedModels))
        );
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
