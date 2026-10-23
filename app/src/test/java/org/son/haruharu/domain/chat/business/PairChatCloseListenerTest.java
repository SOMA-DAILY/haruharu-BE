package org.son.haruharu.domain.chat.business;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.chat.business.event.ChatRoomClosedEvent;
import org.son.haruharu.domain.chat.implement.ChatRoomPair;
import org.son.haruharu.domain.chat.implement.ChatRoomPairReader;
import org.son.haruharu.domain.chat.implement.ChatRoomWriter;
import org.son.haruharu.domain.room.business.event.RoomEndedEvent;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PairChatCloseListenerTest {

    @Mock ChatRoomWriter writer;
    @Mock ChatRoomPairReader pairs;
    @Mock ApplicationEventPublisher events;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC);
    private static final Long ROOM = 10L;

    PairChatCloseListener listener;

    @BeforeEach
    void setUp() {
        listener = new PairChatCloseListener(writer, pairs, events, clock);
    }

    @Test
    @DisplayName("방이 끝나면 열린 PAIR 를 한 번에 닫고, 방마다 쌍의 두 사람에게 종료를 알린다")
    void givenOpenPairs_whenRoomEnded_thenClosesAllAndNotifiesBothUsersOfEachPair() {
        LocalDateTime createdAt = LocalDateTime.now(clock).minusDays(1);
        given(pairs.findOpenByRoomId(ROOM)).willReturn(List.of(
                ChatRoomPair.of(31L, ROOM, 7L, 3L, createdAt), ChatRoomPair.of(32L, ROOM, 3L, 9L, createdAt)));

        listener.onRoomEnded(new RoomEndedEvent(ROOM));

        then(writer).should().closePairs(ROOM, LocalDateTime.now(clock));
        then(events).should().publishEvent(new ChatRoomClosedEvent(31L, List.of(3L, 7L)));
        then(events).should().publishEvent(new ChatRoomClosedEvent(32L, List.of(3L, 9L)));
        then(events).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("열린 PAIR 가 없으면(없거나 이미 닫힘) 닫기도 알림도 하지 않는다")
    void givenNoOpenPair_whenRoomEnded_thenNothing() {
        given(pairs.findOpenByRoomId(ROOM)).willReturn(List.of());

        listener.onRoomEnded(new RoomEndedEvent(ROOM));

        then(writer).shouldHaveNoInteractions();
        then(events).shouldHaveNoInteractions();
    }
}
