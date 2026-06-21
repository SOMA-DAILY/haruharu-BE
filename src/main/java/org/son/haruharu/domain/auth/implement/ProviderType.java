package org.son.haruharu.domain.auth.implement;

import org.son.haruharu.domain.auth.implement.exception.UnsupportedProviderTypeException;

public enum ProviderType {
    KAKAO, APPLE;

    public static ProviderType from(String value) {
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new UnsupportedProviderTypeException(value);
        }
    }
}
