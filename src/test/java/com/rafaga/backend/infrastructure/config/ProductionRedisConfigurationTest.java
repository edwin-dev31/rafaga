package com.rafaga.backend.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class ProductionRedisConfigurationTest {
    @Test
    void productionYamlDoesNotDefineEmptyRedisClusterBlockForStandaloneDeployments() throws Exception {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load(
                "application-production",
                new ClassPathResource("application-production.yaml"));

        assertThat(sources).isNotEmpty();
        PropertySource<?> source = sources.getFirst();
        assertThat(source.containsProperty("spring.data.redis.host")).isTrue();
        assertThat(source.containsProperty("spring.data.redis.cluster.nodes")).isFalse();
        assertThat(source.containsProperty("spring.data.redis.cluster.max-redirects")).isFalse();
    }
}
