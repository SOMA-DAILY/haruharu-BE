package org.son.haruharu.domain.auth.implement;

public record UserCredential(
        Long id,
        Long userId,
        String providerType,
        String providerId
) {}
