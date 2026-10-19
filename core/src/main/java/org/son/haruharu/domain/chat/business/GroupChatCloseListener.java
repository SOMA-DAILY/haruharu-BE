package org.son.haruharu.domain.chat.business;

import lombok.RequiredArgsConstructor;
import java.time.Clock;
import java.time.LocalDateTime;
import org.son.haruharu.domain.chat.business.event.ChatRoomClosedEvent;
import org.son.haruharu.domain.chat.implement.ChatRoomMember;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberReader;
import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.son.haruharu.domain.chat.implement.ChatRoomWriter;
import org.son.haruharu.domain.room.business.event.RoomEndedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

/** app 배치와 admin 취소 모두 원본 방 트랜잭션 안에서 채팅 종료 상태를 확정한다. */
@Component
@RequiredArgsConstructor
public class GroupChatCloseListener {
    private final ChatRoomWriter writer;
    private final ChatRoomReader rooms;
    private final ChatRoomMemberReader members;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onRoomEnded(RoomEndedEvent event) {
        if (!writer.closeGroup(event.roomId(), LocalDateTime.now(clock))) return;
        rooms.findByRoomId(event.roomId()).ifPresent(chat ->
                events.publishEvent(new ChatRoomClosedEvent(chat.id(),
                        members.findByChatRoomId(chat.id()).stream()
                                .filter(member -> !member.hasLeft()).map(ChatRoomMember::userId).toList())));
    }
}
