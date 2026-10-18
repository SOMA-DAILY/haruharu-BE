package org.son.haruharu.domain.room.business;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.implement.InviteCodeGenerator;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomInvite;
import org.son.haruharu.domain.room.implement.RoomInviteReader;
import org.son.haruharu.domain.room.implement.RoomInviteStatus;
import org.son.haruharu.domain.room.implement.RoomInviteWriter;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.exception.InvalidRoomInviteMaxUsesException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** 초대코드 발급·재발급(#312·#318). 코드 입장은 RoomJoinServiceTest. */
@ExtendWith(MockitoExtension.class)
class RoomInviteServiceTest {

    private static final Long ROOM_ID = 10L;
    private static final String CODE = "ABJ234";

    @Mock RoomInviteReader roomInviteReader;
    @Mock RoomInviteWriter roomInviteWriter;
    @Mock RoomReader roomReader;
    @Mock InviteCodeGenerator inviteCodeGenerator;

    RoomInviteService service;

    @BeforeEach
    void setUp() {
        service = new RoomInviteService(roomInviteReader, roomInviteWriter, roomReader, inviteCodeGenerator);
    }

    private RoomInvite activeInvite() {
        return new RoomInvite(5L, ROOM_ID, CODE, RoomInviteStatus.ACTIVE, 6, 0, null, "admin");
    }

    private Room roomWithStatus(RoomStatus status) {
        return new Room(ROOM_ID, status, LocalDate.of(2026, 6, 24), 3, null, null);
    }

    @Test
    @DisplayName("issueFor는 생성기가 만든 코드로 ACTIVE 코드를 발급한다")
    void givenRoom_whenIssueFor_thenGeneratesCodeAndSavesActive() {
        given(inviteCodeGenerator.generate()).willReturn("K7M2QX");
        given(roomInviteWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        RoomInvite issued = service.issueFor(ROOM_ID, 6, null, "admin:1");

        assertThat(issued.code()).isEqualTo("K7M2QX");
        assertThat(issued.status()).isEqualTo(RoomInviteStatus.ACTIVE);
        assertThat(issued.usedCount()).isZero();
    }

    @Test
    @DisplayName("issue는 non-positive maxUses를 저장 전에 도메인 오류로 거부한다")
    void givenNonPositiveMaxUses_whenIssue_thenRejectsBeforePersistence() {
        assertThatThrownBy(() -> service.issue(ROOM_ID, CODE, 0, null, "admin:1"))
                .isInstanceOf(InvalidRoomInviteMaxUsesException.class);

        verifyNoInteractions(roomInviteWriter);
    }

    @Test
    @DisplayName("reissueFor는 room lock 뒤 기존 ACTIVE 코드를 닫고 새 코드를 발급한다")
    void givenActiveInviteExists_whenReissueFor_thenLocksRoomBeforeInviteHistory() {
        given(roomReader.findByIdForUpdate(ROOM_ID)).willReturn(Optional.of(roomWithStatus(RoomStatus.PENDING)));
        given(roomInviteReader.findByRoomId(ROOM_ID)).willReturn(List.of(activeInvite()));
        given(inviteCodeGenerator.generate()).willReturn("NEM234");
        given(roomInviteWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        RoomInvite reissued = service.reissueFor(ROOM_ID, 6, null, "admin:1");

        assertThat(reissued.code()).isEqualTo("NEM234");
        InOrder order = inOrder(roomReader, roomInviteReader, roomInviteWriter);
        order.verify(roomReader).findByIdForUpdate(ROOM_ID);
        order.verify(roomInviteReader).findByRoomId(ROOM_ID);
        order.verify(roomInviteWriter).save(argThat(invite -> invite.status() == RoomInviteStatus.EXPIRED));
        order.verify(roomInviteWriter).save(argThat(invite -> invite.status() == RoomInviteStatus.ACTIVE));
    }

    @Test
    @DisplayName("reissueFor는 이미 EXPIRED인 코드는 다시 저장하지 않는다")
    void givenExpiredInvite_whenReissueFor_thenSavesOnlyNewCode() {
        RoomInvite alreadyExpired = new RoomInvite(9L, ROOM_ID, "NLD234", RoomInviteStatus.EXPIRED,
                6, 6, null, "admin");
        given(roomReader.findByIdForUpdate(ROOM_ID)).willReturn(Optional.of(roomWithStatus(RoomStatus.PENDING)));
        given(roomInviteReader.findByRoomId(ROOM_ID)).willReturn(List.of(alreadyExpired));
        given(inviteCodeGenerator.generate()).willReturn("ZZZ999");
        given(roomInviteWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        service.reissueFor(ROOM_ID, 6, null, "admin:1");

        verify(roomInviteWriter, times(1)).save(any());
    }
}
