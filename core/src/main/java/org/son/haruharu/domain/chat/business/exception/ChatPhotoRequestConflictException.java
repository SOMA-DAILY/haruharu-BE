package org.son.haruharu.domain.chat.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class ChatPhotoRequestConflictException extends ConflictException {
    public ChatPhotoRequestConflictException() {
        super("CHAT_PHOTO_REQUEST_CONFLICT", "이미 사용한 사진 전송 요청입니다.");
    }
}
