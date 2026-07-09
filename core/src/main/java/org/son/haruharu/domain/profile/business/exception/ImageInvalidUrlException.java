package org.son.haruharu.domain.profile.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class ImageInvalidUrlException extends BadRequestException {

    public ImageInvalidUrlException() {
        super("IMAGE_400_INVALID_URL", "본인 소유의 업로드 URL만 저장할 수 있습니다.");
    }
}
