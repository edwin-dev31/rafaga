package com.rafaga.backend.identity.infrastructure.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limits for public or expensive operations. Values are intentionally kept in
 * configuration so production can tune them without a code release.
 */
@ConfigurationProperties(prefix = "rafaga.rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    private Duration window = Duration.ofMinutes(1);
    private int loginPerWindow = 10;
    private int passwordChangePerWindow = 5;
    private int registrationPerWindow = 3;
    private int mediaUploadPerWindow = 12;
    private int mediaConfirmationPerWindow = 24;
    private int guestCartMutationPerWindow = 30;
    private int checkoutPerWindow = 8;
    private int orderCreationPerWindow = 12;
    private int maxFallbackEntries = 10_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getWindow() {
        return window;
    }

    public void setWindow(Duration window) {
        this.window = window;
    }

    public int getLoginPerWindow() {
        return loginPerWindow;
    }

    public void setLoginPerWindow(int loginPerWindow) {
        this.loginPerWindow = loginPerWindow;
    }

    public int getPasswordChangePerWindow() {
        return passwordChangePerWindow;
    }

    public void setPasswordChangePerWindow(int passwordChangePerWindow) {
        this.passwordChangePerWindow = passwordChangePerWindow;
    }

    public int getRegistrationPerWindow() {
        return registrationPerWindow;
    }

    public void setRegistrationPerWindow(int registrationPerWindow) {
        this.registrationPerWindow = registrationPerWindow;
    }

    public int getMediaUploadPerWindow() {
        return mediaUploadPerWindow;
    }

    public void setMediaUploadPerWindow(int mediaUploadPerWindow) {
        this.mediaUploadPerWindow = mediaUploadPerWindow;
    }

    public int getMediaConfirmationPerWindow() {
        return mediaConfirmationPerWindow;
    }

    public void setMediaConfirmationPerWindow(int mediaConfirmationPerWindow) {
        this.mediaConfirmationPerWindow = mediaConfirmationPerWindow;
    }

    public int getGuestCartMutationPerWindow() {
        return guestCartMutationPerWindow;
    }

    public void setGuestCartMutationPerWindow(int guestCartMutationPerWindow) {
        this.guestCartMutationPerWindow = guestCartMutationPerWindow;
    }

    public int getCheckoutPerWindow() {
        return checkoutPerWindow;
    }

    public void setCheckoutPerWindow(int checkoutPerWindow) {
        this.checkoutPerWindow = checkoutPerWindow;
    }

    public int getOrderCreationPerWindow() {
        return orderCreationPerWindow;
    }

    public void setOrderCreationPerWindow(int orderCreationPerWindow) {
        this.orderCreationPerWindow = orderCreationPerWindow;
    }

    public int getMaxFallbackEntries() {
        return maxFallbackEntries;
    }

    public void setMaxFallbackEntries(int maxFallbackEntries) {
        this.maxFallbackEntries = maxFallbackEntries;
    }
}
