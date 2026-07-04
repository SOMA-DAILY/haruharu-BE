package org.son.haruharu.domain.chat.implement;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ChatRoomMemberReader {

    private final ChatRoomMemberRepository chatRoomMemberRepository;

    public ChatRoomMemberReader(ChatRoomMemberRepository chatRoomMemberRepository) {
        this.chatRoomMemberRepository = chatRoomMemberRepository;
    }

    public Optional<ChatRoomMember> findMembership(Long chatRoomId, Long userId) {
        return chatRoomMemberRepository.findByChatRoomIdAndUserId(chatRoomId, userId);
    }

    public List<ChatRoomMember> findActiveByUserId(Long userId) {
        return chatRoomMemberRepository.findActiveByUserId(userId);
    }

    public List<ChatRoomMember> findByChatRoomId(Long chatRoomId) {
        return chatRoomMemberRepository.findByChatRoomId(chatRoomId);
    }
}
