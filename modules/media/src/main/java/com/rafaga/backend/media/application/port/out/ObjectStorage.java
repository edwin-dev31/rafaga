package com.rafaga.backend.media.application.port.out;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public interface ObjectStorage {
    PresignedUpload createUploadUrl(String uploadKey, String contentType, Duration expiry);
    void upload(String objectKey, byte[] data, String contentType);
    InputStream download(String objectKey);
    void delete(String objectKey);
    boolean exists(String objectKey);
    Metadata getMetadata(String objectKey);
    List<Key> listByTenantPrefix(String tenantId);

    record PresignedUpload(String uploadUrl, String uploadKey, Instant expiresAt) {}
    record Metadata(String contentType, long sizeBytes) {}
    record Key(String key) {}
}
