package com.rafaga.backend.tenant.application.port;

/** Safe exception for Cloudflare API failures without leaking credentials or raw headers. */
public class CloudflareProvisioningException extends RuntimeException {
    private final String failureCode;
    private final boolean retryable;

    public CloudflareProvisioningException(String failureCode, String message, boolean retryable) {
        super(message);
        this.failureCode = failureCode;
        this.retryable = retryable;
    }

    public CloudflareProvisioningException(String failureCode, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.failureCode = failureCode;
        this.retryable = retryable;
    }

    public String failureCode() {
        return failureCode;
    }

    public boolean retryable() {
        return retryable;
    }
}
