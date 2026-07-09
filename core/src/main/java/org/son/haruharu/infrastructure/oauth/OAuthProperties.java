package org.son.haruharu.infrastructure.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "oauth")
public record OAuthProperties(Map<String, ProviderConfig> providers) {

    public ProviderConfig getProvider(String name) {
        ProviderConfig config = providers().get(name.toLowerCase());
        if (config == null) {
            throw new IllegalStateException("OAuth provider not configured: " + name);
        }
        return config;
    }

    public record ProviderConfig(String clientId, String issuer, String publicKeyUri) {}
}
