package org.son.haruharu.domain.profile.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class ImageMaxExceededException extends BadRequestException {

    public ImageMaxExceededException() {
        super("IMAGE_400_MAX_EXCEEDED", "최대 이미지 개수를 초과할 수 없습니다.");
    }
}
