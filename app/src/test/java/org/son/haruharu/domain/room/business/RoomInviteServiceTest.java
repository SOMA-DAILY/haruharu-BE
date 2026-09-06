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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.common.time.AdjustableClock;
import org.son.haruharu.domain.room.business.dto.JoinRoomResponse;
import org.son.haruharu.domain.room.business.event.RoomMemberJoinedEvent;
import org.son.haruharu.domain.room.business.exception.InviteCodeNotFoundException;
import org.son.haruharu.domain.room.business.exception.InviteCodeNotUsableException;
import org.son.haruharu.domain.room.implement.InviteCodeGenerator;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomInvite;
import org.son.haruharu.domain.room.implement.RoomInviteReader;
import org.son.haruharu.domain.room.implement.RoomInviteStatus;
import org.son.haruharu.domain.room.implement.RoomInviteWriter;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.exception.InvalidRoomInviteMaxUsesException;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RoomInviteServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Long USER_ID = 1L;
    private static final Long ROOM_ID = 10L;
    private static final String CODE = "ABJ234";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 24, 12, 0);

    @Mock RoomInviteReader roomInviteReader;
    @Mock RoomInviteWriter roomInviteWriter;
    @Mock RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock RoomMemberWriter roomMemberWriter;
    @Mock InviteCodeGenerator inviteCodeGenerator;
    @Mock org.son.haruharu.domain.profile.implement.UserProfileReader userProfileReader;
    @Mock ApplicationEventPublisher eventPublisher;

    RoomInviteService service;

    private final Clock clock = Clock.fixed(
            ZonedDateTime.of(2026, 6, 24, 12, 0, 0, 0, SEOUL).toInstant(), SEOUL);

    @BeforeEach
    void setUp() {
        service = serviceWithClock(clock);
    }

    private RoomInviteService serviceWithClock(Clock serviceClock) {
        return new RoomInviteService(roomInviteReader, roomInviteWriter, roomReader,
                roomMemberReader, roomMemberWriter, inviteCodeGenerator, userProfileReader,
                eventPublisher, serviceClock);
    }

    private RoomInvite activeInvite() {
        return new RoomInvite(5L, ROOM_ID, CODE, RoomInviteStatus.ACTIVE, 6, 0, null, "admin");
    }

    private RoomInvite activeInvite(int maxUses) {
        return new RoomInvite(5L, ROOM_ID, CODE, RoomInviteStatus.ACTIVE, maxUses, 0, null, "admin");
    }

    private Room roomWithStatus(RoomStatus status) {
        return new Room(ROOM_ID, status, LocalDate.of(2026, 6, 24), 3, null, null);
    }

    private void givenJoinCandidate(Room room, RoomInvite lockedInvite) {
        given(roomInviteReader.findRoomIdByCode(CODE)).willReturn(Optional.of(ROOM_ID));
        given(roomReader.findByIdForUpdate(ROOM_ID)).willReturn(Optional.of(room));
        given(roomInviteReader.findByCodeForUpdate(CODE)).willReturn(Optional.of(lockedInvite));
    }

    @Test
    @DisplayName("새 멤버 입장은 코드를 정규화하고 room-first 잠금 뒤 한 번 저장·증가·이벤트 발행한다")
    void givenNewMembership_whenJoin_thenNormalizesLocksAndWritesExactlyOnce() {
        givenJoinCandidate(roomWithStatus(RoomStatus.PENDING), activeInvite());
        given(roomMemberReader.findByRoomIdAndUserId(ROOM_ID, USER_ID)).willReturn(Optional.empty());
        given(roomMemberReader.countByRoomId(ROOM_ID)).willReturn(5L);

        JoinRoomResponse response = service.join(USER_ID, "  abj234  ");

        assertThat(response.roomId()).isEqualTo(ROOM_ID);
        InOrder order = inOrder(roomInviteReader, roomReader, roomMemberReader, roomMemberWriter,
                roomInviteWriter, eventPublisher);
        order.verify(roomInviteReader).findRoomIdByCode(CODE);
        order.verify(roomReader).findByIdForUpdate(ROOM_ID);
        order.verify(roomInviteReader).findByCodeForUpdate(CODE);
        order.verify(roomMemberReader).findByRoomIdAndUserId(ROOM_ID, USER_ID);
        order.verify(roomMemberReader).countByRoomId(ROOM_ID);
        order.verify(roomMemberWriter).save(argThat(member -> member.roomId().equals(ROOM_ID)
                && member.userId().equals(USER_ID) && member.joinedAt().equals(NOW)));
        order.verify(roomInviteWriter).save(argThat(invite -> invite.usedCount() == 1));
        order.verify(eventPublisher).publishEvent(new RoomMemberJoinedEvent(ROOM_ID, USER_ID, NOW));
    }

    @Test
    @DisplayName("프로필 성별이 있으면 입장 시 room_member.gender 로 스냅샷된다(#392)")
    void givenProfileGender_whenJoin_thenSnapshotsGenderToRoomMember() {
        givenJoinCandidate(roomWithStatus(RoomStatus.ACTIVE), activeInvite());
        given(userProfileReader.findByUserId(USER_ID)).willReturn(java.util.Optional.of(
                new org.son.haruharu.domain.profile.implement.UserProfile(
                        1L, USER_ID, null, null, null, null, "하루", null, null,
                        org.son.haruharu.domain.profile.implement.Gender.MALE)));

        service.join(USER_ID, CODE);

        verify(roomMemberWriter).save(argThat(member -> "MALE".equals(member.gender())));
    }

    @Test
    @DisplayName("프로필·성별이 없으면 입장 시 room_member.gender·pseudonym 모두 null 이고 성별 카운트도 조회하지 않는다")
    void givenNoProfileGender_whenJoin_thenRoomMemberGenderAndPseudonymAreNull() {
        givenJoinCandidate(roomWithStatus(RoomStatus.ACTIVE), activeInvite());
        given(userProfileReader.findByUserId(USER_ID)).willReturn(java.util.Optional.empty());

        service.join(USER_ID, CODE);

        verify(roomMemberWriter).save(argThat(member -> member.gender() == null && member.pseudonym() == null));
        verify(roomMemberReader, never()).countByRoomIdAndGender(any(), any());
    }

    @Test
    @DisplayName("입장 시 같은 성별 순번(0-based)대로 방 세션 가명이 배정된다(#393)")
    void givenExistingSameGenderMembers_whenJoin_thenAssignsPseudonymByJoinOrder() {
        givenJoinCandidate(roomWithStatus(RoomStatus.ACTIVE), activeInvite());
        given(userProfileReader.findByUserId(USER_ID)).willReturn(java.util.Optional.of(
                new org.son.haruharu.domain.profile.implement.UserProfile(
                        1L, USER_ID, null, null, null, null, "하루", null, null,
                        org.son.haruharu.domain.profile.implement.Gender.MALE)));
        // 이미 남성 2명이 입장했으므로 3번째(index 2) → 남성 풀 세 번째 "현우"
        given(roomMemberReader.countByRoomIdAndGender(ROOM_ID, "MALE")).willReturn(2L);

        service.join(USER_ID, CODE);

        verify(roomMemberWriter).save(argThat(member -> "현우".equals(member.pseudonym())));
    }

    @Test
    @DisplayName("형식이 잘못된 코드는 저장소 접근 없이 InviteCodeNotUsableException")
    void givenInvalidNormalizedCode_whenJoin_thenThrowsWithoutRepositoryCalls() {
        assertThatThrownBy(() -> service.join(USER_ID, " I01234 "))
                .isInstanceOf(InviteCodeNotUsableException.class);

        verifyNoInteractions(roomInviteReader, roomReader, roomMemberReader, roomMemberWriter,
                roomInviteWriter, eventPublisher);
    }

    @Test
    @DisplayName("존재하지 않는 유효 형식 코드는 InviteCodeNotFoundException")
    void givenUnknownCode_whenJoin_thenNotFound() {
        given(roomInviteReader.findRoomIdByCode(CODE)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.join(USER_ID, CODE))
                .isInstanceOf(InviteCodeNotFoundException.class);
        verify(roomReader, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("room lock 뒤 코드가 다른 방으로 바뀌면 부수 효과 없이 거부한다")
    void givenInviteChangesAfterDiscovery_whenJoin_thenRejectsWithoutWrites() {
        RoomInvite changed = new RoomInvite(5L, 99L, CODE, RoomInviteStatus.ACTIVE, 6, 0, null, "admin");
        given(roomInviteReader.findRoomIdByCode(CODE)).willReturn(Optional.of(ROOM_ID));
        given(roomReader.findByIdForUpdate(ROOM_ID)).willReturn(Optional.of(roomWithStatus(RoomStatus.PENDING)));
        given(roomInviteReader.findByCodeForUpdate(CODE)).willReturn(Optional.of(changed));

        assertThatThrownBy(() -> service.join(USER_ID, CODE))
                .isInstanceOf(InviteCodeNotUsableException.class);
        verifyNoInteractions(roomMemberWriter, roomInviteWriter, eventPublisher);
    }

    @Test
    @DisplayName("같은 방 기존 멤버도 만료 코드를 사용하면 멱등보다 코드 lifecycle 거부가 우선한다")
    void givenExistingMembershipAndExpiredInvite_whenJoinSameRoom_thenRejectsBeforeIdempotency() {
        RoomInvite expired = new RoomInvite(5L, ROOM_ID, CODE, RoomInviteStatus.EXPIRED, 6, 6,
                NOW.minusMinutes(1), "admin");
        givenJoinCandidate(roomWithStatus(RoomStatus.ACTIVE), expired);

        assertThatThrownBy(() -> service.join(USER_ID, CODE))
                .isInstanceOf(InviteCodeNotUsableException.class);

        verify(roomMemberReader, never()).findByRoomIdAndUserId(any(), any());
        verify(roomMemberReader, never()).countByRoomId(any());
        verifyNoInteractions(roomMemberWriter, roomInviteWriter, eventPublisher);
    }

    @Test
    @DisplayName("같은 방 기존 멤버는 정원이 찬 ACTIVE 코드에도 기존 roomId를 받고 쓰기·이벤트가 없다")
    void givenExistingMembershipAndFullInvite_whenJoinSameRoom_thenReturnsWithoutCountWritesOrEvent() {
        RoomInvite full = new RoomInvite(5L, ROOM_ID, CODE, RoomInviteStatus.ACTIVE, 1, 1,
                null, "admin");
        givenJoinCandidate(roomWithStatus(RoomStatus.ACTIVE), full);
        given(roomMemberReader.findByRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(RoomMember.of(ROOM_ID, USER_ID, null, null, NOW.minusDays(1))));

        assertThat(service.join(USER_ID, CODE).roomId()).isEqualTo(ROOM_ID);

        verify(roomMemberReader, never()).countByRoomId(any());
        verifyNoInteractions(roomMemberWriter, roomInviteWriter, eventPublisher);
    }

    @Test
    @DisplayName("다른 현재 방 멤버십은 대상 방의 새 멤버십 생성을 막지 않는다")
    void givenMembershipInDifferentCurrentRoom_whenJoin_thenAddsSecondMembership() {
        givenJoinCandidate(roomWithStatus(RoomStatus.ACTIVE), activeInvite());
        given(roomMemberReader.findByRoomIdAndUserId(ROOM_ID, USER_ID)).willReturn(Optional.empty());
        given(roomMemberReader.countByRoomId(ROOM_ID)).willReturn(1L);

        assertThat(service.join(USER_ID, CODE).roomId()).isEqualTo(ROOM_ID);

        verify(roomMemberWriter).save(argThat(member -> member.roomId().equals(ROOM_ID)));
    }

    @Test
    @DisplayName("실제 active 멤버가 maxUses=2에 도달하면 invite 사용량과 무관하게 부수 효과 없이 거부한다")
    void givenActiveMembersAtInviteMaxUses_whenJoin_thenThrowsWithoutWrites() {
        givenJoinCandidate(roomWithStatus(RoomStatus.ACTIVE), activeInvite(2));
        given(roomMemberReader.findByRoomIdAndUserId(ROOM_ID, USER_ID)).willReturn(Optional.empty());
        given(roomMemberReader.countByRoomId(ROOM_ID)).willReturn(2L);

        assertThatThrownBy(() -> service.join(USER_ID, CODE))
                .isInstanceOf(InviteCodeNotUsableException.class);
        verifyNoInteractions(roomMemberWriter, roomInviteWriter, eventPublisher);
    }

    @Test
    @DisplayName("maxUses=8인 재발급 코드는 기존 6명 active member가 있어도 즉시 새 멤버를 받는다")
    void givenReissuedInviteWithLargerCapacity_whenJoin_thenUsesNewInviteCapacity() {
        givenJoinCandidate(roomWithStatus(RoomStatus.ACTIVE), activeInvite(8));
        given(roomMemberReader.findByRoomIdAndUserId(ROOM_ID, USER_ID)).willReturn(Optional.empty());
        given(roomMemberReader.countByRoomId(ROOM_ID)).willReturn(6L);

        assertThat(service.join(USER_ID, CODE).roomId()).isEqualTo(ROOM_ID);

        verify(roomMemberWriter).save(any());
        verify(roomInviteWriter).save(argThat(invite -> invite.maxUses() == 8 && invite.usedCount() == 1));
    }

    @Test
    @DisplayName("maxUses=null인 코드는 active member가 여섯 명을 넘어도 새 멤버를 받는다")
    void givenUnlimitedInvite_whenJoinAfterSixMembers_thenSucceeds() {
        RoomInvite unlimited = new RoomInvite(5L, ROOM_ID, CODE, RoomInviteStatus.ACTIVE, null, 17, null, "admin");
        givenJoinCandidate(roomWithStatus(RoomStatus.ACTIVE), unlimited);
        given(roomMemberReader.findByRoomIdAndUserId(ROOM_ID, USER_ID)).willReturn(Optional.empty());
        given(roomMemberReader.countByRoomId(ROOM_ID)).willReturn(6L);

        assertThat(service.join(USER_ID, CODE).roomId()).isEqualTo(ROOM_ID);

        verify(roomMemberWriter).save(any());
        verify(roomInviteWriter).save(argThat(invite -> invite.usedCount() == 18));
    }

    @ParameterizedTest
    @EnumSource(value = RoomStatus.class, names = {"PENDING", "ACTIVE", "SELECTING"})
    @DisplayName("PENDING·ACTIVE·SELECTING 방은 새 멤버 입장을 허용한다")
    void givenJoinableRoomStatus_whenJoin_thenSucceeds(RoomStatus status) {
        givenJoinCandidate(roomWithStatus(status), activeInvite());
        given(roomMemberReader.findByRoomIdAndUserId(ROOM_ID, USER_ID)).willReturn(Optional.empty());
        given(roomMemberReader.countByRoomId(ROOM_ID)).willReturn(0L);

        assertThat(service.join(USER_ID, CODE).roomId()).isEqualTo(ROOM_ID);
    }

    @ParameterizedTest
    @EnumSource(value = RoomStatus.class, names = {"ENDED", "CANCELLED"})
    @DisplayName("ENDED·CANCELLED 방은 기존 멤버도 부수 효과 없이 거부한다")
    void givenTerminalRoom_whenJoin_thenRejectsEvenExistingMember(RoomStatus status) {
        givenJoinCandidate(roomWithStatus(status), activeInvite());

        assertThatThrownBy(() -> service.join(USER_ID, CODE))
                .isInstanceOf(InviteCodeNotUsableException.class);
        verify(roomMemberReader, never()).findByRoomIdAndUserId(any(), any());
        verifyNoInteractions(roomMemberWriter, roomInviteWriter, eventPublisher);
    }

    @Test
    @DisplayName("비활성·만료 코드는 신규 멤버에게 부수 효과 없이 거부된다")
    void givenInactiveOrExpiredInvite_whenNewMemberJoins_thenRejectsWithoutWrites() {
        RoomInvite expired = new RoomInvite(5L, ROOM_ID, CODE, RoomInviteStatus.ACTIVE, 6, 0,
                NOW.minusSeconds(1), "admin");
        givenJoinCandidate(roomWithStatus(RoomStatus.ACTIVE), expired);

        assertThatThrownBy(() -> service.join(USER_ID, CODE))
                .isInstanceOf(InviteCodeNotUsableException.class);
        verify(roomMemberReader, never()).findByRoomIdAndUserId(any(), any());
        verify(roomMemberReader, never()).countByRoomId(any());
        verifyNoInteractions(roomMemberWriter, roomInviteWriter, eventPublisher);
    }

    @Test
    @DisplayName("room lock 대기 중 invite가 만료되면 lock 이후 시각으로 거부하고 쓰기·이벤트가 없다")
    void givenInviteExpiresWhileWaitingForRoomLock_whenJoin_thenRejectsAtSerializedTime() {
        LocalDateTime beforeExpiry = NOW;
        LocalDateTime afterExpiry = NOW.plusMinutes(2);
        AdjustableClock advancingClock = new AdjustableClock(clock);
        advancingClock.fix(beforeExpiry);
        RoomInvite expiring = new RoomInvite(5L, ROOM_ID, CODE, RoomInviteStatus.ACTIVE, 6, 0,
                NOW.plusMinutes(1), "admin");
        RoomInviteService boundaryService = serviceWithClock(advancingClock);
        given(roomInviteReader.findRoomIdByCode(CODE)).willReturn(Optional.of(ROOM_ID));
        given(roomReader.findByIdForUpdate(ROOM_ID)).willAnswer(invocation -> {
            advancingClock.fix(afterExpiry);
            return Optional.of(roomWithStatus(RoomStatus.ACTIVE));
        });
        given(roomInviteReader.findByCodeForUpdate(CODE)).willReturn(Optional.of(expiring));

        assertThatThrownBy(() -> boundaryService.join(USER_ID, CODE))
                .isInstanceOf(InviteCodeNotUsableException.class);
        verify(roomMemberReader, never()).findByRoomIdAndUserId(any(), any());
        verify(roomMemberReader, never()).countByRoomId(any());
        verifyNoInteractions(roomMemberWriter, roomInviteWriter, eventPublisher);
    }

    @Test
    @DisplayName("room lock 대기 중 방이 종료되면 기존 멤버 멱등보다 terminal 거부가 우선한다")
    void givenRoomEndsWhileWaitingForRoomLock_whenExistingMemberJoins_thenTerminalWins() {
        LocalDateTime beforeEnd = NOW;
        LocalDateTime afterEnd = NOW.plusMinutes(2);
        AdjustableClock advancingClock = new AdjustableClock(clock);
        advancingClock.fix(beforeEnd);
        RoomInviteService boundaryService = serviceWithClock(advancingClock);
        Room scheduled = new Room(ROOM_ID, RoomStatus.ACTIVE, NOW.toLocalDate(), 3, NOW.minusDays(1), null,
                NOW.minusDays(1), NOW.plusHours(1), NOW.plusHours(2), NOW.plusHours(3), NOW.plusMinutes(1));
        given(roomInviteReader.findRoomIdByCode(CODE)).willReturn(Optional.of(ROOM_ID));
        given(roomReader.findByIdForUpdate(ROOM_ID)).willAnswer(invocation -> {
            advancingClock.fix(afterEnd);
            return Optional.of(scheduled);
        });
        given(roomInviteReader.findByCodeForUpdate(CODE)).willReturn(Optional.of(activeInvite()));

        assertThatThrownBy(() -> boundaryService.join(USER_ID, CODE))
                .isInstanceOf(InviteCodeNotUsableException.class);
        verify(roomMemberReader, never()).findByRoomIdAndUserId(any(), any());
        verifyNoInteractions(roomMemberWriter, roomInviteWriter, eventPublisher);
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
