package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
import org.son.haruharu.domain.room.business.dto.JoinRoomResponse;
import org.son.haruharu.domain.room.business.event.RoomMemberJoinedEvent;
import org.son.haruharu.domain.room.business.exception.InviteCodeNotFoundException;
import org.son.haruharu.domain.room.business.exception.IdentityRequiredException;
import org.son.haruharu.domain.room.business.exception.InviteCodeNotUsableException;
import org.son.haruharu.domain.room.implement.InviteCodeGenerator;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomEntryPath;
import org.son.haruharu.domain.room.implement.RoomInvite;
import org.son.haruharu.domain.room.implement.RoomInviteReader;
import org.son.haruharu.domain.room.implement.RoomInviteStatus;
import org.son.haruharu.domain.room.implement.RoomInviteWriter;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.common.exception.ForbiddenException;
import org.son.haruharu.domain.identity.implement.UserIdentity;
import org.son.haruharu.domain.identity.implement.UserIdentityReader;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RoomJoinServiceTest {

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
    @Mock org.son.haruharu.domain.profile.implement.UserProfileReader userProfileReader;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock UserIdentityReader userIdentityReader;

    RoomJoinService service;

    private final Clock clock = Clock.fixed(
            ZonedDateTime.of(2026, 6, 24, 12, 0, 0, 0, SEOUL).toInstant(), SEOUL);

    @BeforeEach
    void setUp() {
        service = serviceWithClock(clock);
    }

    private RoomJoinService serviceWithClock(Clock serviceClock) {
        return service(serviceClock, false);
    }

    private RoomJoinService service(Clock serviceClock, boolean identityRequired) {
        // 입장 공통 컴포넌트(#513)는 실제 객체로 두고 그 아래 협력자만 목으로 둔다 — 초대코드 입장의 저장·이벤트 동작을 그대로 검증한다.
        RoomMemberJoiner joiner = new RoomMemberJoiner(roomMemberReader, roomMemberWriter, eventPublisher);
        return new RoomJoinService(roomInviteReader, roomInviteWriter, roomReader,
                roomMemberReader, joiner, userProfileReader, userIdentityReader, serviceClock, identityRequired);
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
        given(roomMemberReader.countByRoomIdAndEntryPath(ROOM_ID, RoomEntryPath.INVITE)).willReturn(5L);

        JoinRoomResponse response = service.join(USER_ID, "  abj234  ");

        assertThat(response.roomId()).isEqualTo(ROOM_ID);
        InOrder order = inOrder(roomInviteReader, roomReader, roomMemberReader, roomMemberWriter,
                roomInviteWriter, eventPublisher);
        order.verify(roomInviteReader).findRoomIdByCode(CODE);
        order.verify(roomReader).findByIdForUpdate(ROOM_ID);
        order.verify(roomInviteReader).findByCodeForUpdate(CODE);
        order.verify(roomMemberReader).findByRoomIdAndUserId(ROOM_ID, USER_ID);
        order.verify(roomMemberReader).countByRoomIdAndEntryPath(ROOM_ID, RoomEntryPath.INVITE);
        order.verify(roomMemberWriter).save(argThat(member -> member.roomId().equals(ROOM_ID)
                && member.userId().equals(USER_ID) && member.joinedAt().equals(NOW)));
        order.verify(eventPublisher).publishEvent(new RoomMemberJoinedEvent(ROOM_ID, USER_ID, NOW));
        order.verify(roomInviteWriter).save(argThat(invite -> invite.usedCount() == 1));
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
        verify(roomMemberReader, never()).countByRoomIdAndEntryPath(any(), any());
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

        verify(roomMemberReader, never()).countByRoomIdAndEntryPath(any(), any());
        verifyNoInteractions(roomMemberWriter, roomInviteWriter, eventPublisher);
    }

    @Test
    @DisplayName("다른 현재 방 멤버십은 대상 방의 새 멤버십 생성을 막지 않는다")
    void givenMembershipInDifferentCurrentRoom_whenJoin_thenAddsSecondMembership() {
        givenJoinCandidate(roomWithStatus(RoomStatus.ACTIVE), activeInvite());
        given(roomMemberReader.findByRoomIdAndUserId(ROOM_ID, USER_ID)).willReturn(Optional.empty());
        given(roomMemberReader.countByRoomIdAndEntryPath(ROOM_ID, RoomEntryPath.INVITE)).willReturn(1L);

        assertThat(service.join(USER_ID, CODE).roomId()).isEqualTo(ROOM_ID);

        verify(roomMemberWriter).save(argThat(member -> member.roomId().equals(ROOM_ID)));
    }

    @Test
    @DisplayName("실제 active 멤버가 maxUses=2에 도달하면 invite 사용량과 무관하게 부수 효과 없이 거부한다")
    void givenActiveMembersAtInviteMaxUses_whenJoin_thenThrowsWithoutWrites() {
        givenJoinCandidate(roomWithStatus(RoomStatus.ACTIVE), activeInvite(2));
        given(roomMemberReader.findByRoomIdAndUserId(ROOM_ID, USER_ID)).willReturn(Optional.empty());
        given(roomMemberReader.countByRoomIdAndEntryPath(ROOM_ID, RoomEntryPath.INVITE)).willReturn(2L);

        assertThatThrownBy(() -> service.join(USER_ID, CODE))
                .isInstanceOf(InviteCodeNotUsableException.class);
        verifyNoInteractions(roomMemberWriter, roomInviteWriter, eventPublisher);
    }

    @Test
    @DisplayName("앱 신청자가 섞인 방에서 초대코드 정원은 초대코드로 들어온 인원만 센다(#513)")
    void givenApplicationMembersInRoom_whenJoin_thenCapacityCountsInviteMembersOnly() {
        // 방 전체 5명(앱 신청 3 + 초대 2), 초대코드 정원 3 → 웹 몫 1자리가 남아 있다.
        givenJoinCandidate(roomWithStatus(RoomStatus.PENDING), activeInvite(3));
        given(roomMemberReader.findByRoomIdAndUserId(ROOM_ID, USER_ID)).willReturn(Optional.empty());
        given(roomMemberReader.countByRoomIdAndEntryPath(ROOM_ID, RoomEntryPath.INVITE)).willReturn(2L);

        assertThat(service.join(USER_ID, CODE).roomId()).isEqualTo(ROOM_ID);

        verify(roomMemberReader, never()).countByRoomId(any());
        verify(roomMemberWriter).save(argThat(member -> member.entryPath() == RoomEntryPath.INVITE
                && member.applicationId() == null));
    }

    @Test
    @DisplayName("maxUses=8인 재발급 코드는 기존 6명 active member가 있어도 즉시 새 멤버를 받는다")
    void givenReissuedInviteWithLargerCapacity_whenJoin_thenUsesNewInviteCapacity() {
        givenJoinCandidate(roomWithStatus(RoomStatus.ACTIVE), activeInvite(8));
        given(roomMemberReader.findByRoomIdAndUserId(ROOM_ID, USER_ID)).willReturn(Optional.empty());
        given(roomMemberReader.countByRoomIdAndEntryPath(ROOM_ID, RoomEntryPath.INVITE)).willReturn(6L);

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
        given(roomMemberReader.countByRoomIdAndEntryPath(ROOM_ID, RoomEntryPath.INVITE)).willReturn(6L);

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
        given(roomMemberReader.countByRoomIdAndEntryPath(ROOM_ID, RoomEntryPath.INVITE)).willReturn(0L);

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
        verify(roomMemberReader, never()).countByRoomIdAndEntryPath(any(), any());
        verifyNoInteractions(roomMemberWriter, roomInviteWriter, eventPublisher);
    }

    @Test
    @DisplayName("room lock 대기 중 invite가 만료되면 lock 이후 시각으로 거부하고 쓰기·이벤트가 없다")
    void givenInviteExpiresWhileWaitingForRoomLock_whenJoin_thenRejectsAtSerializedTime() {
        LocalDateTime beforeExpiry = NOW;
        LocalDateTime afterExpiry = NOW.plusMinutes(2);
        MutableClock advancingClock = new MutableClock(clock);
        advancingClock.fix(beforeExpiry);
        RoomInvite expiring = new RoomInvite(5L, ROOM_ID, CODE, RoomInviteStatus.ACTIVE, 6, 0,
                NOW.plusMinutes(1), "admin");
        RoomJoinService boundaryService = serviceWithClock(advancingClock);
        given(roomInviteReader.findRoomIdByCode(CODE)).willReturn(Optional.of(ROOM_ID));
        given(roomReader.findByIdForUpdate(ROOM_ID)).willAnswer(invocation -> {
            advancingClock.fix(afterExpiry);
            return Optional.of(roomWithStatus(RoomStatus.ACTIVE));
        });
        given(roomInviteReader.findByCodeForUpdate(CODE)).willReturn(Optional.of(expiring));

        assertThatThrownBy(() -> boundaryService.join(USER_ID, CODE))
                .isInstanceOf(InviteCodeNotUsableException.class);
        verify(roomMemberReader, never()).findByRoomIdAndUserId(any(), any());
        verify(roomMemberReader, never()).countByRoomIdAndEntryPath(any(), any());
        verifyNoInteractions(roomMemberWriter, roomInviteWriter, eventPublisher);
    }

    @Test
    @DisplayName("room lock 대기 중 방이 종료되면 기존 멤버 멱등보다 terminal 거부가 우선한다")
    void givenRoomEndsWhileWaitingForRoomLock_whenExistingMemberJoins_thenTerminalWins() {
        LocalDateTime beforeEnd = NOW;
        LocalDateTime afterEnd = NOW.plusMinutes(2);
        MutableClock advancingClock = new MutableClock(clock);
        advancingClock.fix(beforeEnd);
        RoomJoinService boundaryService = serviceWithClock(advancingClock);
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

    // ---- PASS 본인확인 게이트(#514) ----

    private UserIdentity passIdentity(String gender, LocalDate birthday) {
        return new UserIdentity(7L, USER_ID, "ci", "di", "01000000000", birthday, gender, "홍길동",
                "SKT", false, NOW.minusDays(1));
    }

    private void givenNewInviteMember() {
        givenJoinCandidate(roomWithStatus(RoomStatus.ACTIVE), activeInvite());
        given(roomMemberReader.findByRoomIdAndUserId(ROOM_ID, USER_ID)).willReturn(Optional.empty());
        given(roomMemberReader.countByRoomIdAndEntryPath(ROOM_ID, RoomEntryPath.INVITE)).willReturn(0L);
    }

    private void givenProfileGender(org.son.haruharu.domain.profile.implement.Gender gender) {
        given(userProfileReader.findByUserId(USER_ID)).willReturn(Optional.of(
                new org.son.haruharu.domain.profile.implement.UserProfile(
                        1L, USER_ID, null, null, null, null, "하루", null, null, gender)));
    }

    @Test
    @DisplayName("플래그 ON: PASS 미인증 계정의 새 입장은 403 IDENTITY_REQUIRED 이고 멤버·사용 횟수·이벤트가 남지 않는다")
    void givenIdentityRequiredAndNoPass_whenNewJoin_thenRejectsWithoutWrites() {
        RoomJoinService gated = service(clock, true);
        givenNewInviteMember();
        given(userIdentityReader.findByUserId(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> gated.join(USER_ID, CODE))
                .isInstanceOf(IdentityRequiredException.class)
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "IDENTITY_REQUIRED");
        verifyNoInteractions(roomMemberWriter, roomInviteWriter, eventPublisher);
    }

    @Test
    @DisplayName("플래그 ON: 이미 멤버인 PASS 미인증 계정의 재입장은 지금처럼 roomId 를 받는다")
    void givenIdentityRequiredAndExistingMemberWithoutPass_whenJoinAgain_thenReturnsRoom() {
        RoomJoinService gated = service(clock, true);
        givenJoinCandidate(roomWithStatus(RoomStatus.ACTIVE), activeInvite());
        given(roomMemberReader.findByRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(RoomMember.of(ROOM_ID, USER_ID, "MALE", null, NOW.minusDays(1))));

        assertThat(gated.join(USER_ID, CODE).roomId()).isEqualTo(ROOM_ID);

        verifyNoInteractions(userIdentityReader, roomMemberWriter, roomInviteWriter, eventPublisher);
    }

    @Test
    @DisplayName("플래그 ON: 코드 정원이 찼으면 PASS 안내보다 코드 사용 불가가 먼저다")
    void givenIdentityRequiredAndFullInvite_whenJoinWithoutPass_thenInviteErrorWins() {
        RoomJoinService gated = service(clock, true);
        givenJoinCandidate(roomWithStatus(RoomStatus.ACTIVE), activeInvite(2));
        given(roomMemberReader.findByRoomIdAndUserId(ROOM_ID, USER_ID)).willReturn(Optional.empty());
        given(roomMemberReader.countByRoomIdAndEntryPath(ROOM_ID, RoomEntryPath.INVITE)).willReturn(2L);

        assertThatThrownBy(() -> gated.join(USER_ID, CODE))
                .isInstanceOf(InviteCodeNotUsableException.class);
        verifyNoInteractions(userIdentityReader, roomMemberWriter, roomInviteWriter, eventPublisher);
    }

    @Test
    @DisplayName("플래그 ON: PASS 인증 계정은 입장하고 멤버 성별·생년은 PASS 값이다")
    void givenIdentityRequiredAndPass_whenJoin_thenSnapshotsPassGenderAndBirthday() {
        RoomJoinService gated = service(clock, true);
        givenNewInviteMember();
        given(userIdentityReader.findByUserId(USER_ID))
                .willReturn(Optional.of(passIdentity("FEMALE", LocalDate.of(2001, 3, 4))));

        assertThat(gated.join(USER_ID, CODE).roomId()).isEqualTo(ROOM_ID);

        verify(roomMemberWriter).save(argThat(member -> "FEMALE".equals(member.gender())
                && LocalDate.of(2001, 3, 4).equals(member.birthday())
                && member.entryPath() == RoomEntryPath.INVITE));
        verify(roomInviteWriter).save(argThat(invite -> invite.usedCount() == 1));
    }

    @Test
    @DisplayName("PASS 성별이 프로필 성별과 다르면 PASS 성별을 쓴다(플래그와 무관)")
    void givenPassAndDifferentProfileGender_whenJoin_thenPassGenderWins() {
        givenNewInviteMember();
        lenient().when(userProfileReader.findByUserId(USER_ID)).thenReturn(Optional.of(
                new org.son.haruharu.domain.profile.implement.UserProfile(
                        1L, USER_ID, null, null, null, null, "하루", null, null,
                        org.son.haruharu.domain.profile.implement.Gender.MALE)));
        given(userIdentityReader.findByUserId(USER_ID))
                .willReturn(Optional.of(passIdentity("FEMALE", LocalDate.of(2000, 1, 2))));

        service.join(USER_ID, CODE);

        verify(roomMemberWriter).save(argThat(member -> "FEMALE".equals(member.gender())
                && LocalDate.of(2000, 1, 2).equals(member.birthday())));
        verify(roomMemberReader).countByRoomIdAndGender(ROOM_ID, "FEMALE");
    }

    @Test
    @DisplayName("PASS 성별이 남녀가 아니면 프로필로 대신하지 않고 성별 미정(null)으로 두며 생년은 PASS 값이다")
    void givenPassWithUnsupportedGender_whenJoin_thenGenderNullAndBirthdayFromPass() {
        givenNewInviteMember();
        lenient().when(userProfileReader.findByUserId(USER_ID)).thenReturn(Optional.of(
                new org.son.haruharu.domain.profile.implement.UserProfile(
                        1L, USER_ID, null, null, null, null, "하루", null, null,
                        org.son.haruharu.domain.profile.implement.Gender.MALE)));
        given(userIdentityReader.findByUserId(USER_ID))
                .willReturn(Optional.of(passIdentity("OTHER", LocalDate.of(1999, 5, 6))));

        service.join(USER_ID, CODE);

        verify(roomMemberWriter).save(argThat(member -> member.gender() == null && member.pseudonym() == null
                && LocalDate.of(1999, 5, 6).equals(member.birthday())));
    }

    @Test
    @DisplayName("플래그 OFF: PASS 미인증 계정도 지금처럼 입장하고 프로필 성별을 쓰며 생년은 비어 있다")
    void givenGateOffAndNoPass_whenJoin_thenProfileGenderAndNoBirthday() {
        givenNewInviteMember();
        givenProfileGender(org.son.haruharu.domain.profile.implement.Gender.MALE);
        given(userIdentityReader.findByUserId(USER_ID)).willReturn(Optional.empty());

        assertThat(service.join(USER_ID, CODE).roomId()).isEqualTo(ROOM_ID);

        verify(roomMemberWriter).save(argThat(member -> "MALE".equals(member.gender()) && member.birthday() == null));
    }

    private static final class MutableClock extends Clock {
        private final ZoneId zone;
        private final AtomicReference<Instant> instant;

        private MutableClock(Clock clock) {
            this(clock.getZone(), new AtomicReference<>(clock.instant()));
        }

        private MutableClock(ZoneId zone, AtomicReference<Instant> instant) {
            this.zone = zone;
            this.instant = instant;
        }

        void fix(LocalDateTime time) {
            instant.set(time.atZone(zone).toInstant());
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(zone, instant);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }

}
