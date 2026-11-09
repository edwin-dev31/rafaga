package com.rafaga.backend.cart.application.service.storefront;

import com.rafaga.backend.common.application.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Manages opaque guest session identifiers backed by Redis.
 * Each session is bound to a tenant and expires after seven days.
 */
@Service
public class GuestSessionService {

    private static final String KEY_PREFIX = "guest-session:";
    private static final Duration TTL = Duration.ofDays(7);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    public GuestSessionService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Creates a new guest session and returns the opaque identifier. */
    public String createSession() {
        String tenantId = TenantContext.getCurrentTenantId();
        String sessionId = generateSessionId();
        String hash = sha256(sessionId);
        String key = KEY_PREFIX + tenantId + ":" + hash;
        redis.opsForValue().set(key, sessionId, TTL);
        return sessionId;
    }

    /**
     * Validates a guest session identifier and returns the hashed value for cart binding.
     * Returns empty if the session is invalid or expired.
     */
    public Optional<String> validateSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        String tenantId = TenantContext.getCurrentTenantId();
        String hash = sha256(sessionId);
        String key = KEY_PREFIX + tenantId + ":" + hash;
        String stored = redis.opsForValue().get(key);
        if (sessionId.equals(stored)) {
            // Refresh TTL on valid access
            redis.expire(key, TTL);
            return Optional.of(hash);
        }
        return Optional.empty();
    }

    /** Touches the session TTL to keep it alive. */
    public boolean touchSession(String sessionId) {
        String tenantId = TenantContext.getCurrentTenantId();
        String hash = sha256(sessionId);
        String key = KEY_PREFIX + tenantId + ":" + hash;
        Boolean exists = redis.hasKey(key);
        if (Boolean.TRUE.equals(exists)) {
            redis.expire(key, TTL);
            return true;
        }
        return false;
    }

    private String generateSessionId() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute SHA-256 hash", e);
        }
    }
}
