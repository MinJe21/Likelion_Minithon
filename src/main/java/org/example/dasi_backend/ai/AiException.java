package org.example.dasi_backend.ai;

import org.springframework.http.HttpStatus;

/** AI API 공통 오류. code 는 프론트 명세의 영어 코드. */
public class AiException extends RuntimeException {

    public enum Code {
        INVALID_REQUEST(HttpStatus.BAD_REQUEST),
        INVALID_SOURCE_TYPE(HttpStatus.BAD_REQUEST),
        SCHOOL_DATA_MISSING(HttpStatus.BAD_REQUEST),
        IDEA_REQUIRED(HttpStatus.BAD_REQUEST),
        IDEA_TOO_SHORT(HttpStatus.BAD_REQUEST),
        IDEA_TOO_LONG(HttpStatus.BAD_REQUEST),
        SELECTED_RECOMMENDATION_REQUIRED(HttpStatus.BAD_REQUEST),
        IDEA_EVALUATION_REQUIRED(HttpStatus.BAD_REQUEST),
        RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS),
        AI_RESPONSE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR),
        AI_RESPONSE_INVALID(HttpStatus.INTERNAL_SERVER_ERROR),
        CHECKPOINT_RESPONSE_INVALID(HttpStatus.INTERNAL_SERVER_ERROR),
        AI_REQUEST_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),
        INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

        public final HttpStatus status;
        Code(HttpStatus status) { this.status = status; }
    }

    private final Code code;

    public AiException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public AiException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() { return code; }
}
