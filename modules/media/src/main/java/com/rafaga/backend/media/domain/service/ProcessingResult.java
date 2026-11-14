package com.rafaga.backend.media.domain.service;

public record ProcessingResult(
    byte[] optimizedBytes,
    String contentType,
    int width,
    int height,
    long sizeBytes,
    boolean success,
    String failureReason,
    String failureCode
) {}
