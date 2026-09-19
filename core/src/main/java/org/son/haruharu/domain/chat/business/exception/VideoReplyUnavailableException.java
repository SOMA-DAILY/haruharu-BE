package org.son.haruharu.domain.chat.business.exception;

import org.son.haruharu.common.exception.ConflictException;

/** 영상 작성자가 단체 채팅을 나가 답장을 받을 수 없는 경우. 요청자 본인의 멤버십 문제(CHAT_NOT_MEMBER)와 구분한다. */
public class VideoReplyUnavailableException extends ConflictException {
    public VideoReplyUnavailableException() {
        super("VIDEO_REPLY_UNAVAILABLE", "답장을 받을 수 없는 영상이에요.");
    }
}
