package org.son.haruharu.domain.mission.business;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.chat.business.OperatorMissionChatService;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.mission.business.command.OperatorMissionCommand;
import org.son.haruharu.domain.mission.implement.*;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import java.time.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OperatorMissionDeliveryServiceTest {
    @Mock OperatorMissionReader reader;
    @Mock OperatorMissionWriter writer;
    @Mock RoomReader roomReader;
    @Mock OperatorMissionChatService chat;
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 4, 12, 0);
    private OperatorMissionDeliveryService service;

    @BeforeEach void setUp() {
        service = new OperatorMissionDeliveryService(reader, writer, roomReader, chat,
                Clock.fixed(now.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul")));
    }
    private OperatorMission mission(LocalDateTime at) {
        return OperatorMission.create(1L, 3L, new OperatorMissionCommand("delivery-test", "DAY1_1", "지금 있는 곳 보여주기", true, at), now.minusHours(1));
    }
    private Room room() { return Room.createScheduled(now.minusHours(1), now.plusDays(2),
            now.plusDays(2).plusHours(1), now.plusDays(2).plusHours(2), now.plusDays(3), 3); }

    @Test @DisplayName("선점 직전에 예약 시각이 미래로 변경됐으면 발송하지 않는다")
    void givenRescheduledMission_whenProcess_thenNoSend() {
        given(reader.getForUpdate(10L)).willReturn(mission(now.plusMinutes(1)));
        service.deliver(10L);
        then(chat).should(never()).send(any());
        then(writer).should(never()).save(any());
    }

    @Test @DisplayName("취소된 예약은 발송하지 않는다")
    void givenCancelledMission_whenProcess_thenNoSend() {
        given(reader.getForUpdate(10L)).willReturn(mission(now).cancelled());
        service.deliver(10L);
        then(chat).should(never()).send(any());
    }

    @Test @DisplayName("촬영 가능한 시각이면 카드 저장과 발송 완료가 같은 트랜잭션에서 처리된다")
    void givenDueMission_whenProcess_thenSentOnce() {
        given(reader.getForUpdate(10L)).willReturn(mission(now));
        given(roomReader.findByIdForUpdate(1L)).willReturn(Optional.of(room()));
        given(chat.send(any())).willReturn(new ChatMessage(99L, 20L, 5L, "지금 있는 곳 보여주기", now));
        service.deliver(10L);
        then(writer).should().save(argThat(m -> m.status() == OperatorMissionStatus.SENT && m.messageId().equals(99L)));
    }

    @Test @DisplayName("방이 취소됐으면 카드나 알림 없이 실패 이유를 저장한다")
    void givenCancelledRoom_whenProcess_thenFailed() {
        given(reader.getForUpdate(10L)).willReturn(mission(now));
        given(roomReader.findByIdForUpdate(1L)).willReturn(Optional.of(room().cancelled()));
        service.deliver(10L);
        then(chat).should(never()).send(any());
        then(writer).should().save(argThat(m -> m.status() == OperatorMissionStatus.FAILED && m.failReason() != null));
    }
}
