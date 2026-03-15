package com.gamepaper.api.error;

public record ErrorResponse(ErrorDetail error) {
    public record ErrorDetail(String code, String message) {}

    public static ErrorResponse of(ErrorCode code, String message) {
        return new ErrorResponse(new ErrorDetail(code.name(), message));
    }
}
