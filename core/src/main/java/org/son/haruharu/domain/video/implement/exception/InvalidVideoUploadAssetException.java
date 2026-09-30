package org.son.haruharu.domain.video.implement.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InvalidVideoUploadAssetException extends BadRequestException {

    public InvalidVideoUploadAssetException() {
        super("VIDEO_UPLOAD_INVALID_ASSET", "지원하지 않는 업로드 파일 종류입니다.");
    }
}
