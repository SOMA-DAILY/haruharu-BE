package org.son.haruharu.domain.chat.implement;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ChatMessageRepository {
    Optional<ChatMessage> findByRequest(Long chatRoomId, Long senderId, String requestId);

    ChatMessage save(ChatMessage message);
    Optional<ChatMessage> findById(Long id);
    /** 답장 원문 배치 조회(#456). 삭제된(deleted_at) 메시지는 빠진다. */
    List<ChatMessage> findAllByIds(java.util.Collection<Long> ids);
    /**
     * 최신순 페이지. cursorId가 null이면 첫 페이지, 아니면 그 id보다 이전(작은) 메시지를 limit개.
     * 삭제된(deletedAt) 메시지는 제외.
     */
    List<ChatMessage> findPage(Long chatRoomId, Long cursorId, int limit);
    /** 방의 마지막(가장 최신) 메시지. 목록 화면의 미리보기용. excludeSenderIds 발신자의 메시지는 건너뛴다(차단 필터, #408). */
    Optional<ChatMessage> findLast(Long chatRoomId, Set<Long> excludeSenderIds);
    /**
     * 안 읽은 메시지 수 = lastReadMessageId보다 큰 id + excludeSenderIds(나 + 내가 차단한 상대, #408) 제외 + 삭제 제외.
     * 순차 Long id라 커서 비교(id > cursor)로 저렴하게 계산된다.
     */
    long countUnread(Long chatRoomId, Long lastReadMessageId, Set<Long> excludeSenderIds);

    /** 운영 글 삭제(#457). 이미 지웠으면 0(멱등). */
    int markAdminDeleted(Long id, java.time.LocalDateTime now);
    /** 운영자가 지웠지만 아직 MESSAGE_DELETED 를 방송하지 않은 메시지. */
    List<ChatMessage> findPendingDeleteBroadcasts(int limit);
    int markDeleteBroadcast(Long id, java.time.LocalDateTime now);

    /** 데모 정리 전용 하드삭제(#266). 전역 soft-delete라 재시드 시 누적되므로 물리 삭제한다. */
    void hardDeleteByChatRoomId(Long chatRoomId);
}
