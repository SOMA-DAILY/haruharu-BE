package org.son.haruharu.domain.chat.implement;

import org.springframework.stereotype.Component;

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

    public Optional<ChatMessage> findLast(Long chatRoomId, Set<Long> excludeSenderIds) {
        return chatMessageRepository.findLast(chatRoomId, excludeSenderIds);
    }

    public long countUnread(Long chatRoomId, Long lastReadMessageId, Set<Long> excludeSenderIds) {
        return chatMessageRepository.countUnread(chatRoomId, lastReadMessageId, excludeSenderIds);
    }
}
