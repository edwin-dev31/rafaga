package com.rafaga.backend.tenant.application.port;

/** Safe, non-secret status metadata returned by Cloudflare custom hostname provisioning. */
public record CustomHostnameStatus(
        String externalId,
        String hostname,
        boolean ownershipVerified,
        boolean certificateReady,
        boolean failed,
        String failureCode,
        String verificationRecordName,
        String verificationRecordValue) {
}
