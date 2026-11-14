package com.rafaga.backend.media.application.exception;

public class MediaConflictException extends RuntimeException {
    private final String code;

    public MediaConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
