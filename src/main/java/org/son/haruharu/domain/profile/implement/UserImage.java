package org.son.haruharu.domain.profile.implement;

import java.util.UUID;

public record UserImage(
        UUID id,
        UUID userId,
        String imageUrl,
        int sortedOrder
) {}
