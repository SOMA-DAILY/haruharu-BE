package org.son.haruharu.domain.video.implement.exception;

import org.son.haruharu.common.exception.ConflictException;

public class VideoUploadUnavailableException extends ConflictException {

    public VideoUploadUnavailableException() {
        super("VIDEO_UPLOAD_UNAVAILABLE", "업로드가 만료되었거나 취소되었습니다. 다시 촬영해 주세요.");
    }
}
