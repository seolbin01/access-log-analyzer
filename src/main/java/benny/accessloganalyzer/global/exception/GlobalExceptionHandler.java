package benny.accessloganalyzer.global.exception;

import java.time.LocalDateTime;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
