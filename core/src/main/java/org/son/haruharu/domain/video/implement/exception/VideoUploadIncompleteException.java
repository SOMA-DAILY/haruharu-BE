package org.son.haruharu.domain.video.implement.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class VideoUploadIncompleteException extends BadRequestException {

    public VideoUploadIncompleteException() {
        super("VIDEO_UPLOAD_INCOMPLETE", "영상과 썸네일 업로드를 확인하지 못했습니다.");
    }
}
