package com.rafaga.backend.tenant.infrastructure.cache;

import com.rafaga.backend.common.application.tenant.TenantContext;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

/** Creates stable cache keys that cannot be shared by two tenant requests. */
@Component("tenantScopedCacheKeyGenerator")
public class TenantScopedCacheKeyGenerator implements KeyGenerator {
    @Override
    public Object generate(Object target, Method method, Object... parameters) {
        String tenantId = TenantContext.getCurrentTenantId();
        if (parameters.length == 0) {
            return tenantId;
        }
        return tenantId + ":" + Arrays.stream(parameters)
                .map(String::valueOf)
                .collect(Collectors.joining(":"));
    }
}
