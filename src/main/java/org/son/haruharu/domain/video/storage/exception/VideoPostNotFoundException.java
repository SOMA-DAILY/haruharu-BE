package org.son.haruharu.domain.video.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class VideoPostNotFoundException extends NotFoundException {

    public VideoPostNotFoundException() {
        super("VIDEO_404_NOT_FOUND", "존재하지 않는 영상입니다.");
    }
}
