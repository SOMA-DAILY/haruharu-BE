package org.son.haruharu.domain.profile.implement;

public record UserImage(
        Long id,
        Long userId,
        String imageUrl,
        int sortedOrder
) {}
