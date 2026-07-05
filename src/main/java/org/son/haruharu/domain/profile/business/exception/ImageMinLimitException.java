package org.son.haruharu.domain.profile.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class ImageMinLimitException extends BadRequestException {

    public ImageMinLimitException() {
        super("IMAGE_400_MIN_LIMIT", "최소 이미지 개수는 1개 이상입니다.");
    }
}
