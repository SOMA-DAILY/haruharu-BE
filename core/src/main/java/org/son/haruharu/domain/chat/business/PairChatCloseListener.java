package org.son.haruharu.domain.chat.business;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.chat.business.event.ChatRoomClosedEvent;
import org.son.haruharu.domain.chat.implement.ChatRoomPair;
import org.son.haruharu.domain.chat.implement.ChatRoomPairReader;
import org.son.haruharu.domain.chat.implement.ChatRoomWriter;
import org.son.haruharu.domain.room.business.event.RoomEndedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방이 끝나면 그 방의 대화 시간 1:1(PAIR) 채팅도 닫는다(#540). GroupChatCloseListener 와 같이 원본 방 트랜잭션 안에서
 * 종료 시각을 확정하고, 닫힌 방마다 구독 종료 이벤트를 보낸다. 이미 닫힌 방은 다시 알리지 않는다.
 * 알림 대상은 쌍 행의 두 사람이다(PAIR 는 나가기가 없다). 방을 잠근 트랜잭션에서 채팅방마다 멤버를 다시 읽지 않는다.
 */
@Component
@RequiredArgsConstructor
public class PairChatCloseListener {
    private final ChatRoomWriter writer;
    private final ChatRoomPairReader pairs;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onRoomEnded(RoomEndedEvent event) {
        List<ChatRoomPair> open = pairs.findOpenByRoomId(event.roomId());
        if (open.isEmpty()) return;
        writer.closePairs(event.roomId(), LocalDateTime.now(clock));
        open.forEach(pair -> events.publishEvent(new ChatRoomClosedEvent(pair.chatRoomId(), pair.userIds())));
    }
}
