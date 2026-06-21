package org.son.haruharu.domain.auth.implement;

import java.util.UUID;

public record UserCredential(
        UUID id,
        UUID userId,
        String providerType,
        String providerId
) {}
