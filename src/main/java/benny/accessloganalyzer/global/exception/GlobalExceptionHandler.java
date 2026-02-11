package benny.accessloganalyzer.global.exception;

import java.time.LocalDateTime;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorResponse body = new ErrorResponse(
                e.getStatus().value(),
                e.getCode(),
                e.getMessage(),
                LocalDateTime.now()
        );
        return ResponseEntity.status(e.getStatus()).body(body);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        ErrorResponse body = new ErrorResponse(
                400,
                "FILE_SIZE_EXCEEDED",
                "파일 크기가 허용 범위를 초과했습니다 (최대 50MB)",
                LocalDateTime.now()
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestPart(MissingServletRequestPartException e) {
        ErrorResponse body = new ErrorResponse(
                400,
                "MISSING_FILE",
                "파일이 누락되었습니다: " + e.getRequestPartName(),
                LocalDateTime.now()
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        ErrorResponse body = new ErrorResponse(
                500,
                "INTERNAL_SERVER_ERROR",
                "서버 내부 오류가 발생했습니다",
                LocalDateTime.now()
        );
        return ResponseEntity.internalServerError().body(body);
    }
}
