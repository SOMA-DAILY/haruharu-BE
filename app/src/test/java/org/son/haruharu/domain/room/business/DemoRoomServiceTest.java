package org.son.haruharu.domain.room.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.business.dto.DemoRoomResponse;
import org.son.haruharu.domain.room.business.exception.DemoRoomNotFoundException;
import org.son.haruharu.domain.identity.business.IdentityService;
import org.son.haruharu.domain.identity.business.dto.VerifiedIdentity;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.son.haruharu.domain.profile.business.ProfileService;
import org.son.haruharu.domain.user.business.UserService;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class DemoRoomServiceTest {

    @Mock RoomReader roomReader;
    @Mock RoomWriter roomWriter;
    @Mock RoomMemberReader roomMemberReader;
    @Mock RoomMemberWriter roomMemberWriter;
    @Mock IdentityService identityService;
    @Mock UserService userService;
    @Mock ProfileService profileService;
    @Mock org.son.haruharu.domain.video.business.VideoService videoService;
    @Mock org.son.haruharu.domain.coin.business.CoinService coinService;
    @Mock org.son.haruharu.domain.room.implement.DemoRoomFinder demoRoomFinder;
    @Mock DemoMatchService demoMatchService;

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final Clock clock = Clock.fixed(ZonedDateTime.of(2026, 7, 20, 12, 0, 0, 0, SEOUL).toInstant(), SEOUL);

    DemoRoomService service;

    @BeforeEach
    void setUp() {
        service = new DemoRoomService(roomWriter, roomMemberReader, roomMemberWriter,
                identityService, userService, profileService, videoService, coinService,
                demoRoomFinder, demoMatchService, clock);
    }

    @Test
    @DisplayName("데모 방이 없으면 Day1 데모 방을 생성하고 본인+가상5명(총 6명)을 멤버로 추가한다")
    void givenNoRoom_whenCreateOrGet_thenCreatesDemoRoomAndSixMembers() {
        Long userId = 42L;
        given(demoRoomFinder.find(userId)).willReturn(Optional.empty());
        given(roomWriter.save(any())).willReturn(
                new Room(1L, RoomStatus.ACTIVE, LocalDate.of(2026, 7, 20), 3,
                        LocalDateTime.of(2026, 7, 20, 9, 0), null, true, 1));
        given(identityService.findVerified(userId))
                .willReturn(Optional.of(new VerifiedIdentity("MALE", LocalDate.of(1995, 6, 15))));
        // 가상 계정 생성은 UserService 위임 — 101..105 순차 반환
        given(userService.createDemoVirtualAccount()).willReturn(101L, 102L, 103L, 104L, 105L);
        // 저장 시 id가 부여된 멤버 반환(피드 시드에 roomMemberId 필요)
        given(roomMemberWriter.save(any())).willAnswer(inv -> {
            RoomMember m = inv.getArgument(0);
            return new RoomMember(900L + m.userId(), m.roomId(), m.userId(), m.gender(), m.birthday(), m.joinedAt());
        });

        DemoRoomResponse result = service.createOrGet(userId);

        assertThat(result.roomId()).isEqualTo(1L);
        assertThat(result.day()).isEqualTo(1);
        ArgumentCaptor<Room> roomCaptor = ArgumentCaptor.forClass(Room.class);
        then(roomWriter).should().save(roomCaptor.capture());
        assertThat(roomCaptor.getValue().demo()).isTrue();
        // 가상 5명 계정·프로필 시드 위임 확인
        then(userService).should(times(5)).createDemoVirtualAccount();
        then(profileService).should(times(5))
                .seedDemoProfile(anyLong(), anyString(),
                        anyString(), anyString());
        // 본인 1 + 가상 5 = 6명 저장
        ArgumentCaptor<RoomMember> memberCaptor = ArgumentCaptor.forClass(RoomMember.class);
        then(roomMemberWriter).should(times(6)).save(memberCaptor.capture());
        assertThat(memberCaptor.getAllValues()).extracting(RoomMember::userId)
                .containsExactly(42L, 101L, 102L, 103L, 104L, 105L);
        // Day1 피드 시드 위임(본인 포함 6명)
        then(videoService).should().seedDemoFeed(anyList(), org.mockito.ArgumentMatchers.eq(1));
    }

    @Test
    @DisplayName("이미 데모 방이 있으면 새로 만들지 않고 그대로 반환한다")
    void givenExistingRoom_whenCreateOrGet_thenReturnsExisting() {
        Long userId = 42L;
        Room existing = new Room(7L, RoomStatus.ACTIVE, LocalDate.of(2026, 7, 20), 3,
                LocalDateTime.of(2026, 7, 20, 9, 0), null, true, 2);
        given(demoRoomFinder.find(userId)).willReturn(Optional.of(existing));

        DemoRoomResponse result = service.createOrGet(userId);

        assertThat(result.roomId()).isEqualTo(7L);
        assertThat(result.day()).isEqualTo(2);
        then(roomWriter).should(never()).save(any());
        then(roomMemberWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("Day 진행 시 demoDay가 1 증가한다")
    void givenDemoRoom_whenAdvanceDay_thenBumpsDemoDay() {
        Long userId = 42L;
        Room room = new Room(7L, RoomStatus.ACTIVE, LocalDate.of(2026, 7, 20), 3,
                LocalDateTime.of(2026, 7, 20, 9, 0), null, true, 1);
        given(demoRoomFinder.find(userId)).willReturn(Optional.of(room));
        given(roomWriter.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(roomMemberReader.getByRoomId(7L)).willReturn(List.of(
                new RoomMember(70L, 7L, userId, "MALE", null, LocalDateTime.now(clock))));

        DemoRoomResponse result = service.advanceDay(userId);

        assertThat(result.day()).isEqualTo(2);
        // 새 Day(2)로 진행했으므로 그 Day 피드가 시드된다
        then(videoService).should().seedDemoFeed(anyList(), org.mockito.ArgumentMatchers.eq(2));
    }

    @Test
    @DisplayName("Day 진행은 totalDays(3)를 넘지 않는다")
    void givenDay3_whenAdvanceDay_thenStaysAt3() {
        Long userId = 42L;
        Room room = new Room(7L, RoomStatus.ACTIVE, LocalDate.of(2026, 7, 20), 3,
                LocalDateTime.of(2026, 7, 20, 9, 0), null, true, 3);
        given(demoRoomFinder.find(userId)).willReturn(Optional.of(room));
        given(roomWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        DemoRoomResponse result = service.advanceDay(userId);

        assertThat(result.day()).isEqualTo(3);
        // 최종일에 머무르면 새 Day 진행이 없으므로 피드 재시드도 하지 않는다(중복 방지)
        then(videoService).should(never()).seedDemoFeed(anyList(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("데모 방이 없는데 Day 진행 시 DemoRoomNotFoundException(404)")
    void givenNoRoom_whenAdvanceDay_thenThrows() {
        Long userId = 42L;
        given(demoRoomFinder.find(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.advanceDay(userId))
                .isInstanceOf(DemoRoomNotFoundException.class);
    }

    @Test
    @DisplayName("데모 리셋 시 Day1로 되돌리고 피드 재시드·구름 리시드한다")
    void givenDemoRoom_whenReset_thenDay1AndReseed() {
        Long userId = 42L;
        Room room = new Room(7L, RoomStatus.ACTIVE, LocalDate.of(2026, 7, 20), 3,
                LocalDateTime.of(2026, 7, 20, 9, 0), null, true, 3);
        given(userService.isDemoAccount(userId)).willReturn(true);
        given(demoRoomFinder.find(userId)).willReturn(Optional.of(room));
        given(roomWriter.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(roomMemberReader.getByRoomId(7L)).willReturn(List.of(
                new RoomMember(70L, 7L, userId, "MALE", null, LocalDateTime.now(clock))));

        DemoRoomResponse result = service.reset(userId);

        assertThat(result.roomId()).isEqualTo(7L);
        assertThat(result.day()).isEqualTo(1);
        then(coinService).should().resetDemoWallet(userId, 200L);
        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        then(roomWriter).should().save(captor.capture());
        assertThat(captor.getValue().demoDay()).isEqualTo(1);
        then(videoService).should().resetDemoFeed(anyList(), org.mockito.ArgumentMatchers.eq(1));
        // 최종선택 결과·채팅까지 정리해야 참가신청부터 재시연된다(#266)
        then(demoMatchService).should().purge(anyList());
    }

    @Test
    @DisplayName("데모 방이 없어도 구름은 리시드하고 방 조작은 하지 않는다")
    void givenNoRoom_whenReset_thenReseedCoinsOnly() {
        Long userId = 42L;
        given(userService.isDemoAccount(userId)).willReturn(true);
        given(demoRoomFinder.find(userId)).willReturn(Optional.empty());

        DemoRoomResponse result = service.reset(userId);

        assertThat(result.roomId()).isNull();
        assertThat(result.day()).isEqualTo(1);
        then(coinService).should().resetDemoWallet(userId, 200L);
        then(roomWriter).should(never()).save(any());
        then(videoService).should(never()).resetDemoFeed(anyList(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("데모 계정이 아니면 리셋을 거부하고 구름도 건드리지 않는다")
    void givenNonDemoAccount_whenReset_thenThrowsAndNoCoinReset() {
        Long userId = 42L;
        given(userService.isDemoAccount(userId)).willReturn(false);

        assertThatThrownBy(() -> service.reset(userId))
                .isInstanceOf(org.son.haruharu.domain.room.business.exception.NotDemoAccountException.class);
        then(coinService).should(never()).resetDemoWallet(anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }
}
