package org.son.haruharu.infrastructure.pass.exception;

import org.son.haruharu.common.exception.BadGatewayException;

public class PassProviderException extends BadGatewayException {
    public PassProviderException() {
        super("PASS_PROVIDER_UNAVAILABLE", "본인인증 서비스에 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
    }
}
