package com.rafaga.backend.media.application.exception;

public class MediaValidationException extends IllegalArgumentException {
    private final String code;

    public MediaValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
