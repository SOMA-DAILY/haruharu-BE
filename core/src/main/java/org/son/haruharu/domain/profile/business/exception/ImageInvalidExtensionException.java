package org.son.haruharu.domain.profile.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class ImageInvalidExtensionException extends BadRequestException {

    public ImageInvalidExtensionException() {
        super("IMAGE_400_INVALID_EXTENSION", "유효하지 않은 이미지 형식입니다.");
    }
}
