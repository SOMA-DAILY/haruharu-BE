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

    /** 나가지 않은 멤버십만 반환한다. */
    public Optional<ChatRoomMember> findActiveMembership(Long chatRoomId, Long userId) {
        return findMembership(chatRoomId, userId).filter(member -> !member.hasLeft());
    }

    public List<ChatRoomMember> findActiveByUserId(Long userId) {
        return chatRoomMemberRepository.findActiveByUserId(userId);
    }

    public List<ChatRoomMember> findByChatRoomId(Long chatRoomId) {
        return chatRoomMemberRepository.findByChatRoomId(chatRoomId);
    }
}
