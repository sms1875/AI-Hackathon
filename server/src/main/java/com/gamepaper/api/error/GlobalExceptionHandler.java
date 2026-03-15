package com.gamepaper.api.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * 전역 예외 처리기.
 * 모든 API 에러를 { "error": { "code": "...", "message": "..." } } 형태로 표준화한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        ErrorCode code = resolveErrorCode(ex);
        ErrorResponse body = ErrorResponse.of(code, ex.getReason() != null ? ex.getReason() : ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        ErrorResponse body = ErrorResponse.of(ErrorCode.INTERNAL_ERROR, "서버 내부 오류가 발생했습니다.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private ErrorCode resolveErrorCode(ResponseStatusException ex) {
        String reason = ex.getReason() != null ? ex.getReason() : "";
        if (reason.contains("게임을 찾을 수 없습니다")) return ErrorCode.GAME_NOT_FOUND;
        if (reason.contains("배경화면을 찾을 수 없습니다")) return ErrorCode.WALLPAPER_NOT_FOUND;
        if (reason.contains("device-id")) return ErrorCode.MISSING_DEVICE_ID;
        if (ex.getStatusCode().value() == 400) return ErrorCode.INVALID_REQUEST;
        return ErrorCode.INTERNAL_ERROR;
    }
}
