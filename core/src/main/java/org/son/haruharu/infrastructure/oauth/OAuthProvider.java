package org.son.haruharu.infrastructure.oauth;

import org.son.haruharu.domain.auth.implement.ProviderType;

public interface OAuthProvider {
    ProviderType supportedType();
    OAuthUserInfo verify(String idToken);
}
