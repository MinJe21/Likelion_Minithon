package org.example.dasi_backend.diagnose;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** 활용모델 후보 (use_models.json). 수요(key_demand) 필드는 제외. */
public record UseModel(
        @JsonProperty("model_id") String modelId,
        @JsonProperty("model_name") String modelName,
        @JsonProperty("required_space") List<String> requiredSpace,
        @JsonProperty("main_strengths") List<String> mainStrengths,
        @JsonProperty("main_risks") List<String> mainRisks
) {}
