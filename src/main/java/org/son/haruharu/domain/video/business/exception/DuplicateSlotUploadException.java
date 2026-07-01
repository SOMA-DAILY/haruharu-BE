package org.son.haruharu.domain.video.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class DuplicateSlotUploadException extends ConflictException {

    public DuplicateSlotUploadException() {
        super("VIDEO_SLOT_ALREADY_UPLOADED", "이미 해당 슬롯에 업로드한 영상이 있습니다. 재촬영하려면 먼저 삭제하세요.");
    }
}
