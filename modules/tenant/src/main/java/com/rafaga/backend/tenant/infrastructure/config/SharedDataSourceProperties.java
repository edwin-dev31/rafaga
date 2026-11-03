package com.rafaga.backend.tenant.infrastructure.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.datasource")
public class SharedDataSourceProperties {
    private String url;
    private String username;
    private String password;
    private String driverClassName;
    private final Hikari hikari = new Hikari();

    public HikariDataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(Objects.requireNonNull(url, "spring.datasource.url must not be null"));
        config.setUsername(Objects.requireNonNull(username, "spring.datasource.username must not be null"));
        config.setPassword(Objects.requireNonNull(password, "spring.datasource.password must not be null"));
        config.setDriverClassName(Objects.requireNonNull(driverClassName, "spring.datasource.driver-class-name must not be null"));
        config.setPoolName(hikari.poolName);
        config.setMaximumPoolSize(hikari.maximumPoolSize);
        config.setMinimumIdle(hikari.minimumIdle);
        config.setConnectionTimeout(hikari.connectionTimeout);
        return new HikariDataSource(config);
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDriverClassName() { return driverClassName; }
    public void setDriverClassName(String driverClassName) { this.driverClassName = driverClassName; }
    public Hikari getHikari() { return hikari; }

    public static class Hikari {
        private String poolName = "RafagaSharedPool";
        private int maximumPoolSize = 50;
        private int minimumIdle = 10;
        private long connectionTimeout = 30000;
        public String getPoolName() { return poolName; }
        public void setPoolName(String poolName) { this.poolName = poolName; }
        public int getMaximumPoolSize() { return maximumPoolSize; }
        public void setMaximumPoolSize(int maximumPoolSize) { this.maximumPoolSize = maximumPoolSize; }
        public int getMinimumIdle() { return minimumIdle; }
        public void setMinimumIdle(int minimumIdle) { this.minimumIdle = minimumIdle; }
        public long getConnectionTimeout() { return connectionTimeout; }
        public void setConnectionTimeout(long connectionTimeout) { this.connectionTimeout = connectionTimeout; }
    }
}
