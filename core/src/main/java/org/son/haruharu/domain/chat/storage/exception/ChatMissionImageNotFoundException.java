package org.son.haruharu.domain.chat.storage.exception;
import org.son.haruharu.common.exception.NotFoundException;
public class ChatMissionImageNotFoundException extends NotFoundException {
    public ChatMissionImageNotFoundException() { super("CHAT_MISSION_IMAGE_NOT_FOUND", "참고 사진을 찾을 수 없어요."); }
}
