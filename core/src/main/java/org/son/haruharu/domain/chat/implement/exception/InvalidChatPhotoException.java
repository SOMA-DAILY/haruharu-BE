package org.son.haruharu.domain.chat.implement.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InvalidChatPhotoException extends BadRequestException {
    public InvalidChatPhotoException() {
        super("CHAT_PHOTO_INVALID", "10MB 이하의 JPG·PNG 사진을 선택해 주세요.");
    }
}
