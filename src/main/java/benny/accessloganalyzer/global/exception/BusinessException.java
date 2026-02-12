package benny.accessloganalyzer.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private BusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static BusinessException analysisNotFound(String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND", message);
    }

    public static BusinessException invalidLogFile(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_LOG_FILE", message);
    }

    public static BusinessException externalApiError(String message) {
        return new BusinessException(HttpStatus.BAD_GATEWAY, "EXTERNAL_API_ERROR", message);
    }

    public static BusinessException analysisQueueFull(String message) {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "ANALYSIS_QUEUE_FULL", message);
    }

    public static BusinessException invalidParameter(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", message);
    }
}
