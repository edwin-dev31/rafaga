package com.rafaga.backend.media.infrastructure.storage;

import com.rafaga.backend.media.application.port.out.ObjectStorage;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fake implementation of ObjectStorage used for tests.
 * Replaces R2ObjectStorage when the "test" profile is active so that
 * Spring Boot can start without real Cloudflare R2 credentials.
 */
@Component
@Profile("test")
public class FakeObjectStorage implements ObjectStorage {

    private final Map<String, StoredObject> store = new ConcurrentHashMap<>();

    private record StoredObject(byte[] data, String contentType, Instant createdAt) {
        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof StoredObject that)) {
                return false;
            }
            return Arrays.equals(data, that.data)
                    && Objects.equals(contentType, that.contentType)
                    && Objects.equals(createdAt, that.createdAt);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(contentType, createdAt);
            return 31 * result + Arrays.hashCode(data);
        }

        @Override
        public String toString() {
            return "StoredObject[data=" + Arrays.toString(data)
                    + ", contentType=" + contentType
                    + ", createdAt=" + createdAt + "]";
        }
    }

    @Override
    public PresignedUpload createUploadUrl(String uploadKey, String contentType, Duration expiry) {
        // In a fake, the "presigned URL" is just a placeholder; the upload
        // is simulated by calling upload() directly.
        Instant expiresAt = Instant.now().plus(expiry);
        return new PresignedUpload("http://fake-presigned-url/" + uploadKey, uploadKey, expiresAt);
    }

    @Override
    public void upload(String objectKey, byte[] data, String contentType) {
        store.put(objectKey, new StoredObject(data.clone(), contentType, Instant.now()));
    }

    @Override
    public InputStream download(String objectKey) {
        StoredObject obj = store.get(objectKey);
        if (obj == null) {
            throw new RuntimeException("NoSuchKey: " + objectKey);
        }
        return new ByteArrayInputStream(obj.data());
    }

    @Override
    public void delete(String objectKey) {
        store.remove(objectKey);
    }

    @Override
    public boolean exists(String objectKey) {
        return store.containsKey(objectKey);
    }

    @Override
    public Metadata getMetadata(String objectKey) {
        StoredObject obj = store.get(objectKey);
        if (obj == null) {
            throw new RuntimeException("NoSuchKey: " + objectKey);
        }
        return new Metadata(obj.contentType(), (long) obj.data().length);
    }

    @Override
    public List<Key> listByTenantPrefix(String tenantId) {
        String prefix = "tenants/" + tenantId + "/";
        List<Key> result = new ArrayList<>();
        for (String key : store.keySet()) {
            if (key.startsWith(prefix)) {
                result.add(new Key(key));
            }
        }
        return result;
    }
}
