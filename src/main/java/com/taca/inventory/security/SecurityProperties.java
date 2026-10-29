package com.taca.inventory.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Security configuration properties for Inventory Service.
 */
@Component
@ConfigurationProperties(prefix = "inventory.security")
public class SecurityProperties {

    /**
     * Shared secret token for inter-service communication (e.g. from Order Service).
     */
    private String internalToken;

    public SecurityProperties() {}

    public SecurityProperties(String internalToken) {
        this.internalToken = internalToken;
    }

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }
}
