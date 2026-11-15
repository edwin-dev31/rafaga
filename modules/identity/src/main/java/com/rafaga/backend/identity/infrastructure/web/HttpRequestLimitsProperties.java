package com.rafaga.backend.identity.infrastructure.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/** Limits declared API request bodies before they reach MVC deserialization. */
@ConfigurationProperties(prefix = "rafaga.web.request-limits")
public class HttpRequestLimitsProperties {
    private DataSize maxContentLength = DataSize.ofMegabytes(1);

    public DataSize getMaxContentLength() {
        return maxContentLength;
    }

    public void setMaxContentLength(DataSize maxContentLength) {
        this.maxContentLength = maxContentLength;
    }
}
