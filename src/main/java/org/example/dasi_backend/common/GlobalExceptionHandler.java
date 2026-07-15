package org.example.dasi_backend.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 개발/디버깅용 전역 예외 핸들러.
 * - @ResponseStatus 가 붙은 예외(404/400 등)는 해당 상태코드와 메시지를 그대로 반환.
 * - 그 외 예외는 500 으로 처리하며 예외 타입/근본원인을 응답 본문에 담고 스택트레이스를 로그로 남긴다.
 * (운영 배포 시에는 상세 메시지 노출을 줄이는 것을 권장)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handle(Exception e) {
        ResponseStatus rs = AnnotatedElementUtils.findMergedAnnotation(e.getClass(), ResponseStatus.class);
        if (rs != null) {
            // 의도된 클라이언트 오류(404/400 등)는 스택트레이스 없이 간단히
            HttpStatus status = rs.value();
            log.warn("{} - {}", status, e.getMessage());
            return ResponseEntity.status(status).body(Map.of(
                    "error", e.getClass().getSimpleName(),
                    "message", String.valueOf(e.getMessage())
            ));
        }

        log.error("요청 처리 중 예외 발생", e);
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", e.getClass().getName(),
                "message", String.valueOf(e.getMessage()),
                "rootCause", root.getClass().getName(),
                "rootMessage", String.valueOf(root.getMessage())
        ));
    }
}
