package org.son.haruharu.domain.mission.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class UploadSlotNotFoundException extends NotFoundException {

    public UploadSlotNotFoundException() {
        super("UPLOAD_SLOT_404_NOT_FOUND", "존재하지 않는 업로드 슬롯입니다.");
    }
}
