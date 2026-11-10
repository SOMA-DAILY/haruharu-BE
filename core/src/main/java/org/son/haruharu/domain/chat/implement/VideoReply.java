package org.son.haruharu.domain.chat.implement;

import org.son.haruharu.domain.chat.implement.exception.VideoReplyInvalidException;
import org.son.haruharu.domain.room.implement.RoomStatus;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 영상 답장 입력. 내용은 앞뒤 공백을 제거하고, 요청 ID는 소문자 표준 UUID로 정규화한다.
 * 대문자 UUID(iOS {@code UUID().uuidString})도 같은 요청으로 취급해 멱등 재시도가 깨지지 않게 한다.
 */
public record VideoReply(Long videoPostId, String content, String requestId) {
    public static final int MAX_CONTENT_LENGTH = 2000;

    public static VideoReply of(Long videoPostId, String content, String requestId) {
        if (content == null || content.isBlank() || content.length() > MAX_CONTENT_LENGTH)
            throw new VideoReplyInvalidException("답장은 1~2000자로 입력해 주세요.");
        return new VideoReply(videoPostId, content.strip(), normalizeRequestId(requestId));
    }

    /** 진행 중이거나 최종 선택 중인 방에서만 답장할 수 있다. */
    public static void validateRoomOpen(RoomStatus status) {
        if (!isRoomOpen(status))
            throw new VideoReplyInvalidException("답장을 보낼 수 없는 방입니다.");
    }

    /** 영상 답장을 보낼 수 있는 방 상태인가(진행 중·최종 선택 중). */
    public static boolean isRoomOpen(RoomStatus status) {
        return status == RoomStatus.ACTIVE || status == RoomStatus.SELECTING;
    }

    /** 같은 요청 ID로 이미 저장된 메시지가 이 입력과 같은 답장인지 확인한다. */
    public boolean matches(ChatMessage saved) {
        return Objects.equals(saved.videoPostId(), videoPostId) && saved.content().equals(content);
    }

    public ChatMessage toMessage(Long chatRoomId, Long senderId) {
        return ChatMessage.videoReply(chatRoomId, senderId, content, videoPostId, requestId);
    }

    private static String normalizeRequestId(String requestId) {
        if (requestId == null) throw new VideoReplyInvalidException("잘못된 전송 요청입니다.");
        String normalized = requestId.toLowerCase(Locale.ROOT);
        try {
            // fromString은 "1-1-1-1-1" 같은 축약형도 받으므로 표준 36자 형식과 일치하는지 다시 확인한다.
            if (UUID.fromString(normalized).toString().equals(normalized)) return normalized;
        } catch (IllegalArgumentException ignored) {
            // 아래에서 같은 예외로 응답한다.
        }
        throw new VideoReplyInvalidException("잘못된 전송 요청입니다.");
    }
}
