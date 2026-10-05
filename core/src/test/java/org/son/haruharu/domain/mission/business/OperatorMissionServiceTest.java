package org.son.haruharu.domain.mission.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.mission.business.command.OperatorMissionCommand;
import org.son.haruharu.domain.mission.implement.*;
import org.son.haruharu.domain.mission.implement.exception.OperatorMissionStateConflictException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;

import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OperatorMissionServiceTest {
    @Mock OperatorMissionReader reader;
    @Mock OperatorMissionWriter writer;
    @Mock RoomReader roomReader;
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 4, 12, 0);
    private final Clock clock = Clock.fixed(now.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
    private OperatorMissionService service;

    @BeforeEach void setUp() { service = new OperatorMissionService(reader, writer, roomReader, clock, org.mockito.Mockito.mock(MissionImageReader.class)); }

    private Room room() { return Room.createScheduled(now.minusHours(1), now.plusDays(2),
            now.plusDays(2).plusHours(1), now.plusDays(2).plusHours(2), now.plusDays(3), 3); }
    private OperatorMissionCommand command(LocalDateTime at) {
        return new OperatorMissionCommand("request-1", "DAY1_1", "지금 있는 곳 보여주기", true, at);
    }

    @Test @DisplayName("동일 요청을 재시도하면 새 예약 없이 기존 결과를 반환한다")
    void givenDuplicateRequest_whenCreate_thenReturnsExisting() {
        OperatorMission existing = OperatorMission.create(1L, 3L, command(now.plusHours(1)), now);
        given(roomReader.findByIdForUpdate(1L)).willReturn(Optional.of(room()));
        given(reader.findByRequestKey(1L, "request-1")).willReturn(Optional.of(existing));
        assertThat(service.create(1L, 3L, command(now.plusHours(1)))).isEqualTo(existing);
        then(writer).should(never()).save(any());
    }

    @Test @DisplayName("예약 대기 중 취소하면 취소 상태를 저장한다")
    void givenScheduledMission_whenCancel_thenCancelled() {
        OperatorMission item = OperatorMission.create(1L, 3L, command(now.plusHours(1)), now);
        given(reader.getForUpdate(10L)).willReturn(item);
        given(writer.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.cancel(1L, 10L).status()).isEqualTo(OperatorMissionStatus.CANCELLED);
    }

    @Test @DisplayName("이미 발송 처리 중이면 예약 수정과 취소가 충돌한다")
    void givenSendingMission_whenEditOrCancel_thenConflict() {
        OperatorMission item = OperatorMission.create(1L, 3L, command(null), now);
        given(reader.getForUpdate(10L)).willReturn(item);
        assertThatThrownBy(() -> service.cancel(1L, 10L)).isInstanceOf(OperatorMissionStateConflictException.class);
        assertThatThrownBy(() -> service.update(1L, 10L, command(now.plusHours(1))))
                .isInstanceOf(OperatorMissionStateConflictException.class);
        then(writer).should(never()).save(any());
    }
}
