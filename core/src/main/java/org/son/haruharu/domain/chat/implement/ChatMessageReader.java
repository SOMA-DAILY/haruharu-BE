package org.son.haruharu.domain.chat.implement;

import org.springframework.stereotype.Component;
import org.son.haruharu.domain.chat.implement.exception.InvalidChatSyncCursorException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class ChatMessageReader {

    private final ChatMessageRepository chatMessageRepository;

    public ChatMessageReader(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    public Optional<ChatMessage> findByRequest(Long chatRoomId, Long senderId, String requestId) {
        return chatMessageRepository.findByRequest(chatRoomId, senderId, requestId);
    }

    public Optional<ChatMessage> findById(Long id) {
        return chatMessageRepository.findById(id);
    }

    public List<ChatMessage> findAllByIds(java.util.Collection<Long> ids) {
        return chatMessageRepository.findAllByIds(ids);
    }

    public List<ChatMessage> findPendingDeleteBroadcasts(int limit) {
        return chatMessageRepository.findPendingDeleteBroadcasts(limit);
    }

    /** 기준 시각 이전 메시지 전체(시간순). 답장 추천의 방 기록 스냅샷에 쓴다(#558). */
    public List<ChatMessage> findHistoryBefore(Long chatRoomId, java.time.LocalDateTime before) {
        return chatMessageRepository.findHistoryBefore(chatRoomId, before);
    }

    public List<ChatMessage> findPage(Long chatRoomId, Long cursorId, int limit) {
        return chatMessageRepository.findPage(chatRoomId, cursorId, limit);
    }

    /**
     * 모든 생성 경로는 같은 방 행 잠금 안에서 ID를 발급하고 저장한다.
     * 따라서 커밋된 head 이하에 더 늦게 커밋되는 메시지가 없다는 전제로 범위를 확정한다.
     * HTTP 페이지 사이에는 DB 트랜잭션을 유지하지 않는다.
     */
    public ChatMessageSyncPage sync(Long chatRoomId, long afterId, Long untilId, int size) {
        if (afterId < 0 || (untilId != null && untilId < afterId)) {
            throw new InvalidChatSyncCursorException();
        }
        long head = chatMessageRepository.findLastIdIncludingDeleted(chatRoomId);
        long target = untilId == null ? head : untilId;
        if (afterId > head || target > head) {
            throw new InvalidChatSyncCursorException();
        }
        int limit = Math.min(Math.max(size, 1), 100);
        List<ChatMessage> rows = chatMessageRepository.findSyncPage(chatRoomId, afterId, target, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<ChatMessage> page = hasMore ? rows.subList(0, limit) : rows;
        long next = hasMore ? page.getLast().id() : target;
        return new ChatMessageSyncPage(page, target, next, hasMore);
    }

    public Optional<ChatMessage> findLast(Long chatRoomId, Set<Long> excludeSenderIds) {
        return chatMessageRepository.findLast(chatRoomId, excludeSenderIds);
    }

    public long countUnread(Long chatRoomId, Long lastReadMessageId, Set<Long> excludeSenderIds) {
        return chatMessageRepository.countUnread(chatRoomId, lastReadMessageId, excludeSenderIds);
    }
}
