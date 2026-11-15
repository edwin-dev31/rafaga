package com.rafaga.backend.infrastructure.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Production enables scheduled work by default. Tests invoke jobs explicitly
 * so an asynchronous scheduler cannot race deterministic assertions.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "rafaga.scheduling", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class SchedulingConfiguration {
}
