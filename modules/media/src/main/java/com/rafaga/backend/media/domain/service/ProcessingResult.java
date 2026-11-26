package com.rafaga.backend.media.domain.service;

import java.util.Arrays;
import java.util.Objects;

public record ProcessingResult(
    byte[] optimizedBytes,
    String contentType,
    int width,
    int height,
    long sizeBytes,
    boolean success,
    String failureReason,
    String failureCode
) {
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProcessingResult that)) {
            return false;
        }
        return width == that.width
                && height == that.height
                && sizeBytes == that.sizeBytes
                && success == that.success
                && Arrays.equals(optimizedBytes, that.optimizedBytes)
                && Objects.equals(contentType, that.contentType)
                && Objects.equals(failureReason, that.failureReason)
                && Objects.equals(failureCode, that.failureCode);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(contentType, width, height, sizeBytes, success, failureReason, failureCode);
        return 31 * result + Arrays.hashCode(optimizedBytes);
    }

    @Override
    public String toString() {
        return "ProcessingResult[optimizedBytes=" + Arrays.toString(optimizedBytes)
                + ", contentType=" + contentType
                + ", width=" + width
                + ", height=" + height
                + ", sizeBytes=" + sizeBytes
                + ", success=" + success
                + ", failureReason=" + failureReason
                + ", failureCode=" + failureCode + "]";
    }
}
