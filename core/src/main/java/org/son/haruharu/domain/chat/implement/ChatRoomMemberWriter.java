package org.son.haruharu.domain.chat.implement;

import org.springframework.stereotype.Component;

@Component
public class ChatRoomMemberWriter {

    private final ChatRoomMemberRepository chatRoomMemberRepository;

    public ChatRoomMemberWriter(ChatRoomMemberRepository chatRoomMemberRepository) {
        this.chatRoomMemberRepository = chatRoomMemberRepository;
    }

    public ChatRoomMember save(ChatRoomMember member) {
        return chatRoomMemberRepository.save(member);
    }

    /** 데모 정리 전용 삭제(#266). */
    public void deleteByChatRoomId(Long chatRoomId) {
        chatRoomMemberRepository.deleteByChatRoomId(chatRoomId);
    }
}
