package org.son.haruharu.domain.auth.implement.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class UnsupportedProviderTypeException extends BadRequestException {

    public UnsupportedProviderTypeException(String providerType) {
        super("AUTH_400_UNSUPPORTED_PROVIDER", "지원하지 않는 OAuth 공급자입니다: " + providerType);
    }
}
