package org.son.haruharu.domain.video.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class SlotWindowClosedException extends BadRequestException {

    public SlotWindowClosedException() {
        super("VIDEO_SLOT_WINDOW_CLOSED", "현재 시각은 해당 슬롯의 업로드 시간이 아닙니다.");
    }
}
