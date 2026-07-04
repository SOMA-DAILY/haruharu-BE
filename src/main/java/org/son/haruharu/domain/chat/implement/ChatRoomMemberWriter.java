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
}
