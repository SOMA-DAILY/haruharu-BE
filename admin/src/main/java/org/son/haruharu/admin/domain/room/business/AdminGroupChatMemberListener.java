package org.son.haruharu.admin.domain.room.business;

import org.son.haruharu.domain.chat.implement.ChatRoomMember;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberReader;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberWriter;
import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.son.haruharu.domain.room.business.event.RoomMemberJoinedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 어드민에서 방에 넣은 신청자(#516)를 이미 열린 그룹채팅에 같은 트랜잭션으로 등록한다.
 * 앱 서버의 GroupChatAutoCreateListener 와 같은 동작이다 — 그쪽 ChatService 는 identity·profile 빈을 끌고 와 어드민에 둘 수 없다.
 * 시작 전 방(그룹채팅 없음)은 할 일이 없다: 방이 시작될 때 앱 서버가 그때 멤버로 그룹채팅을 만든다.
 * 방 행 잠금은 호출측(CohortAssignmentService)이 이미 잡고 있다.
 */
@Component
public class AdminGroupChatMemberListener {

    private final ChatRoomReader chatRoomReader;
    private final ChatRoomMemberReader chatRoomMemberReader;
    private final ChatRoomMemberWriter chatRoomMemberWriter;

    public AdminGroupChatMemberListener(ChatRoomReader chatRoomReader, ChatRoomMemberReader chatRoomMemberReader,
                                        ChatRoomMemberWriter chatRoomMemberWriter) {
        this.chatRoomReader = chatRoomReader;
        this.chatRoomMemberReader = chatRoomMemberReader;
        this.chatRoomMemberWriter = chatRoomMemberWriter;
    }

    @EventListener
    public void handleMemberJoined(RoomMemberJoinedEvent event) {
        chatRoomReader.findByRoomId(event.roomId()).ifPresent(group -> {
            if (chatRoomMemberReader.findMembership(group.id(), event.userId()).isEmpty()) {
                chatRoomMemberWriter.save(ChatRoomMember.join(group.id(), event.userId(), event.joinedAt()));
            }
        });
    }
}
