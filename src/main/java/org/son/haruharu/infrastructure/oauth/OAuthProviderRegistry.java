package org.son.haruharu.infrastructure.oauth;

import org.son.haruharu.domain.auth.implement.ProviderType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OAuthProviderRegistry {

    private final Map<ProviderType, OAuthProvider> providers;

    public OAuthProviderRegistry(List<OAuthProvider> providerList) {
        this.providers = providerList.stream()
            .collect(Collectors.toMap(OAuthProvider::supportedType, Function.identity()));
    }

    public OAuthProvider get(ProviderType type) {
        OAuthProvider provider = providers.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("지원하지 않는 OAuth 공급자: " + type);
        }
        return provider;
    }
}
