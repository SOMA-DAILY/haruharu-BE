package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.business.dto.JoinRoomResponse;
import org.son.haruharu.domain.room.business.exception.AlreadyJoinedRoomException;
import org.son.haruharu.domain.room.business.exception.InviteCodeNotFoundException;
import org.son.haruharu.domain.room.business.exception.InviteCodeNotUsableException;
import org.son.haruharu.domain.room.implement.InviteCodeGenerator;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomInvite;
import org.son.haruharu.domain.room.implement.RoomInviteReader;
import org.son.haruharu.domain.room.implement.RoomInviteStatus;
import org.son.haruharu.domain.room.implement.RoomInviteWriter;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RoomInviteServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Long USER_ID = 1L;
    private static final Long ROOM_ID = 10L;
    private static final String CODE = "ABC123";

    @Mock RoomInviteReader roomInviteReader;
    @Mock RoomInviteWriter roomInviteWriter;
    @Mock RoomReader roomReader;
    @Mock RoomService roomService;
    @Mock RoomMemberWriter roomMemberWriter;
    @Mock InviteCodeGenerator inviteCodeGenerator;

    RoomInviteService service;

    private final Clock clock = Clock.fixed(
            ZonedDateTime.of(2026, 6, 24, 12, 0, 0, 0, SEOUL).toInstant(), SEOUL);

    @BeforeEach
    void setUp() {
        service = new RoomInviteService(roomInviteReader, roomInviteWriter, roomReader,
                roomService, roomMemberWriter, inviteCodeGenerator, clock);
    }

    private RoomInvite activeInvite() {
        return new RoomInvite(5L, ROOM_ID, CODE, RoomInviteStatus.ACTIVE, 6, 0, null, "admin");
    }

    private Room roomWithStatus(RoomStatus status) {
        // startsAt/endsAt null → statusAt은 저장 status를 반환(구모델 폴백 경로 활용)
        return new Room(ROOM_ID, status, LocalDate.of(2026, 6, 24), 3, null, null);
    }

    @Test
    @DisplayName("유효한 코드로 입장 시 gender=null 멤버를 추가하고 usedCount를 올린 뒤 roomId를 반환한다")
    void givenValidCode_whenJoin_thenAddsMemberAndReturnsRoomId() {
        given(roomInviteReader.findByCodeForUpdate(CODE)).willReturn(Optional.of(activeInvite()));
        given(roomReader.findById(ROOM_ID)).willReturn(Optional.of(roomWithStatus(RoomStatus.PENDING)));
        given(roomService.hasCurrentRoom(USER_ID)).willReturn(false);

        JoinRoomResponse res = service.join(USER_ID, CODE);

        assertThat(res.roomId()).isEqualTo(ROOM_ID);
        verify(roomMemberWriter).save(argThat(m ->
                m.roomId().equals(ROOM_ID) && m.userId().equals(USER_ID) && m.gender() == null));
        verify(roomInviteWriter).save(argThat(i -> i.usedCount() == 1));
    }

    @Test
    @DisplayName("존재하지 않는 코드면 InviteCodeNotFoundException")
    void givenUnknownCode_whenJoin_thenNotFound() {
        given(roomInviteReader.findByCodeForUpdate(CODE)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.join(USER_ID, CODE))
                .isInstanceOf(InviteCodeNotFoundException.class);
        verify(roomMemberWriter, never()).save(any());
    }

    @Test
    @DisplayName("만료된 코드면 InviteCodeNotUsableException (방 조회 전에 차단)")
    void givenExpiredCode_whenJoin_thenNotUsable() {
        RoomInvite expired = new RoomInvite(5L, ROOM_ID, CODE, RoomInviteStatus.ACTIVE, 6, 0,
                LocalDateTime.of(2026, 6, 24, 11, 0), "admin");
        given(roomInviteReader.findByCodeForUpdate(CODE)).willReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.join(USER_ID, CODE))
                .isInstanceOf(InviteCodeNotUsableException.class);
        verify(roomMemberWriter, never()).save(any());
    }

    @Test
    @DisplayName("종료된 방이면 InviteCodeNotUsableException")
    void givenEndedRoom_whenJoin_thenNotUsable() {
        given(roomInviteReader.findByCodeForUpdate(CODE)).willReturn(Optional.of(activeInvite()));
        given(roomReader.findById(ROOM_ID)).willReturn(Optional.of(roomWithStatus(RoomStatus.ENDED)));

        assertThatThrownBy(() -> service.join(USER_ID, CODE))
                .isInstanceOf(InviteCodeNotUsableException.class);
        verify(roomMemberWriter, never()).save(any());
    }

    @Test
    @DisplayName("이미 참여 중인 방이 있으면 AlreadyJoinedRoomException")
    void givenAlreadyInCurrentRoom_whenJoin_thenConflict() {
        given(roomInviteReader.findByCodeForUpdate(CODE)).willReturn(Optional.of(activeInvite()));
        given(roomReader.findById(ROOM_ID)).willReturn(Optional.of(roomWithStatus(RoomStatus.PENDING)));
        given(roomService.hasCurrentRoom(USER_ID)).willReturn(true);

        assertThatThrownBy(() -> service.join(USER_ID, CODE))
                .isInstanceOf(AlreadyJoinedRoomException.class);
        verify(roomMemberWriter, never()).save(any());
    }

    @Test
    @DisplayName("issueFor는 생성기가 만든 코드로 ACTIVE 코드를 발급한다 (#318)")
    void givenRoom_whenIssueFor_thenGeneratesCodeAndSavesActive() {
        given(inviteCodeGenerator.generate()).willReturn("K7M2QX");
        given(roomInviteWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        RoomInvite issued = service.issueFor(ROOM_ID, 6, null, "admin:1");

        assertThat(issued.code()).isEqualTo("K7M2QX");
        assertThat(issued.status()).isEqualTo(RoomInviteStatus.ACTIVE);
        assertThat(issued.roomId()).isEqualTo(ROOM_ID);
        assertThat(issued.maxUses()).isEqualTo(6);
        assertThat(issued.usedCount()).isZero();
        assertThat(issued.createdBy()).isEqualTo("admin:1");
    }

    @Test
    @DisplayName("reissueFor는 기존 ACTIVE 코드를 EXPIRED로 만들고 새 코드를 발급한다 (방당 ACTIVE 1개)")
    void givenActiveInviteExists_whenReissueFor_thenExpiresOldAndIssuesNew() {
        given(roomInviteReader.findByRoomId(ROOM_ID)).willReturn(List.of(activeInvite()));
        given(inviteCodeGenerator.generate()).willReturn("NEWC0DE".substring(0, 6));
        given(roomInviteWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        RoomInvite reissued = service.reissueFor(ROOM_ID, 6, null, "admin:1");

        assertThat(reissued.status()).isEqualTo(RoomInviteStatus.ACTIVE);
        assertThat(reissued.code()).isNotEqualTo(CODE);
        verify(roomInviteWriter).save(argThat(i ->
                CODE.equals(i.code()) && i.status() == RoomInviteStatus.EXPIRED));
    }

    @Test
    @DisplayName("reissueFor는 기존 코드가 없어도 새 코드를 발급한다")
    void givenNoInvite_whenReissueFor_thenIssuesNew() {
        given(roomInviteReader.findByRoomId(ROOM_ID)).willReturn(List.of());
        given(inviteCodeGenerator.generate()).willReturn("ZZZ999");
        given(roomInviteWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        assertThat(service.reissueFor(ROOM_ID, 6, null, "admin:1").code()).isEqualTo("ZZZ999");
    }

    @Test
    @DisplayName("reissueFor는 이미 EXPIRED인 코드는 다시 저장하지 않는다")
    void givenExpiredInvite_whenReissueFor_thenSavesOnlyNewCode() {
        RoomInvite alreadyExpired = new RoomInvite(9L, ROOM_ID, "OLDCDE", RoomInviteStatus.EXPIRED, 6, 6, null, "admin");
        given(roomInviteReader.findByRoomId(ROOM_ID)).willReturn(List.of(alreadyExpired));
        given(inviteCodeGenerator.generate()).willReturn("ZZZ999");
        given(roomInviteWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        service.reissueFor(ROOM_ID, 6, null, "admin:1");

        verify(roomInviteWriter, times(1)).save(any());
    }
}
