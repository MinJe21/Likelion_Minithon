package org.example.dasi_backend.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    /** 활용모델 추천 (아이디어 없음) */
    @PostMapping("/recommendations")
    public RecommendationResponse recommendations(@RequestBody RecommendationRequest request) {
        return aiService.recommend(request);
    }

    /** 아이디어 적합성 진단 (아이디어 있음) */
    @PostMapping("/idea-evaluations")
    public IdeaEvaluationResponse ideaEvaluations(@RequestBody IdeaEvaluationRequest request) {
        return aiService.evaluateIdea(request);
    }

    /** 학교별 최종 체크포인트 (추천/아이디어 진단 결과 기반) */
    @PostMapping("/final-checkpoints")
    public FinalCheckpointResponse finalCheckpoints(@RequestBody FinalCheckpointRequest request) {
        return aiService.generateCheckpoints(request);
    }

    // ---------------------------------------------------------------- 오류 처리 (공통 형식)

    @ExceptionHandler(AiException.class)
    public ResponseEntity<ApiError> handleAi(AiException e) {
        String requestId = newRequestId();
        log.warn("[{}] {} - {}", requestId, e.code(), e.getMessage());
        return ResponseEntity.status(e.code().status)
                .body(new ApiError(e.code().name(), e.getMessage(), requestId));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleBadBody(HttpMessageNotReadableException e) {
        String requestId = newRequestId();
        log.warn("[{}] INVALID_REQUEST - {}", requestId, e.getMessage());
        return ResponseEntity.status(AiException.Code.INVALID_REQUEST.status)
                .body(new ApiError("INVALID_REQUEST", "요청 형식이 올바르지 않습니다.", requestId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception e) {
        String requestId = newRequestId();
        log.error("[{}] INTERNAL_SERVER_ERROR", requestId, e);
        return ResponseEntity.status(AiException.Code.INTERNAL_SERVER_ERROR.status)
                .body(new ApiError("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.", requestId));
    }

    private static String newRequestId() {
        return "request_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
