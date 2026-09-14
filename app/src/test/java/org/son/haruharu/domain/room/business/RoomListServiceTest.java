package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.profile.implement.UserImageReader;
import org.son.haruharu.domain.room.business.dto.RoomListTimer;
import org.son.haruharu.domain.room.business.dto.RoomsResponse;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.infrastructure.s3.S3StoragePort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class RoomListServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 26, 12, 0);
    private static final Long USER_ID = 100L;

    @Mock RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock UserImageReader userImageReader;
    @Mock S3StoragePort s3StoragePort;

    private RoomListService roomListService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        roomListService = new RoomListService(roomReader, roomMemberReader, userImageReader, s3StoragePort, clock);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("timerCases")
    @DisplayName("방 목록 타이머는 모든 정확한 경계에서 카드 계약을 따른다")
    void givenScheduledRoom_whenTimerAtBoundary_thenUsesExpectedTypeTargetAndRefresh(
            String name, LocalDateTime now, RoomListTimer.TimerType type,
            LocalDateTime target, LocalDateTime refresh) {
        Room room = scheduledRoom(1L, NOW.minusHours(2), NOW.plusHours(2), NOW.plusHours(4),
                NOW.plusHours(6), NOW.plusHours(8));

        RoomListTimer timer = RoomListTimer.from(room, now);

        assertThat(timer.timerType()).isEqualTo(type);
        assertThat(timer.timerTargetAt()).isEqualTo(target);
        assertThat(timer.refreshAt()).isEqualTo(refresh);
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> timerCases() {
        LocalDateTime starts = NOW.minusHours(2);
        LocalDateTime opens = NOW.plusHours(2);
        LocalDateTime closes = NOW.plusHours(4);
        LocalDateTime result = NOW.plusHours(6);
        LocalDateTime ends = NOW.plusHours(8);
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("start 직전", starts.minusNanos(1), RoomListTimer.TimerType.ROOM_STARTS_AT, starts, starts),
                org.junit.jupiter.params.provider.Arguments.of("start 정확히", starts, RoomListTimer.TimerType.FINAL_SELECTION_OPENS_AT, opens, opens),
                org.junit.jupiter.params.provider.Arguments.of("selection open 정확히", opens, RoomListTimer.TimerType.FINAL_SELECTION_CLOSES_AT, closes, closes),
                org.junit.jupiter.params.provider.Arguments.of("selection close 직전", closes.minusNanos(1), RoomListTimer.TimerType.FINAL_SELECTION_CLOSES_AT, closes, closes),
                org.junit.jupiter.params.provider.Arguments.of("selection close 정확히", closes, RoomListTimer.TimerType.RESULT_AT, result, result),
                org.junit.jupiter.params.provider.Arguments.of("result 직전", result.minusNanos(1), RoomListTimer.TimerType.RESULT_AT, result, result),
                org.junit.jupiter.params.provider.Arguments.of("result 정확히", result, null, null, ends),
                org.junit.jupiter.params.provider.Arguments.of("end 직전", ends.minusNanos(1), null, null, ends),
                org.junit.jupiter.params.provider.Arguments.of("end 정확히", ends, null, null, null));
    }

    @Test
    @DisplayName("selection close와 result가 같으면 그 정확한 시각부터 결과 대기 타이머가 숨겨진다")
    void givenSelectionCloseEqualsResult_whenTimerAtBoundary_thenReturnsOnlyEndRefresh() {
        Room room = scheduledRoom(1L, NOW.minusHours(2), NOW.minusHours(1), NOW,
                NOW, NOW.plusHours(2));

        RoomListTimer timer = RoomListTimer.from(room, NOW);

        assertThat(timer.timerType()).isNull();
        assertThat(timer.timerTargetAt()).isNull();
        assertThat(timer.refreshAt()).isEqualTo(NOW.plusHours(2));
    }

    @Test
    @DisplayName("레거시 또는 불완전 스케줄 방의 목록 타이머는 null로 안전하게 반환한다")
    void givenLegacyOrMalformedSchedule_whenTimer_thenReturnsNullFields() {
        Room legacy = new Room(1L, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 25), 3, NOW.minusDays(1), null);
        Room malformed = new Room(2L, RoomStatus.ACTIVE, NOW.toLocalDate(), 3, NOW.minusHours(1), null,
                NOW.minusHours(1), null, NOW.plusHours(2), NOW.plusHours(3), NOW.plusHours(4));

        assertThat(RoomListTimer.from(legacy, NOW)).isEqualTo(new RoomListTimer(null, null, null));
        assertThat(RoomListTimer.from(malformed, NOW)).isEqualTo(new RoomListTimer(null, null, null));
    }

    @Test
    @DisplayName("현재 방 목록은 하나의 now로 동적 상태·일차를 계산하고 ACTIVE, SELECTING, PENDING 순으로 반환한다")
    void givenSeveralMemberships_whenGetRooms_thenReturnsOneNowCurrentRoomsAndOnlyImageUrls() {
        Room active = scheduledRoom(30L, NOW.minusHours(1), NOW.plusHours(2), NOW.plusHours(3), NOW.plusHours(4), NOW.plusHours(6));
        Room selecting = scheduledRoom(20L, NOW.minusDays(2), NOW.minusHours(1), NOW.plusHours(1), NOW.plusHours(2), NOW.plusDays(1));
        Room pending = scheduledRoom(10L, NOW.plusHours(1), NOW.plusHours(2), NOW.plusHours(3), NOW.plusHours(4), NOW.plusHours(5));
        given(roomMemberReader.findByUserId(USER_ID)).willReturn(List.of(member(1L, 30L, USER_ID), member(2L, 20L, USER_ID), member(3L, 10L, USER_ID)));
        given(roomReader.findCurrentAll(List.of(30L, 20L, 10L), NOW)).willReturn(List.of(pending, selecting, active));
        given(roomMemberReader.findByRoomIds(anyCollection())).willReturn(List.of(
                member(11L, 30L, USER_ID), member(12L, 20L, USER_ID), member(13L, 10L, USER_ID)));
        given(userImageReader.getRepresentativeImageUrls(anyCollection())).willReturn(Map.of());

        RoomsResponse result = roomListService.getRooms(USER_ID);

        assertThat(result.now()).isEqualTo(NOW);
        assertThat(result.rooms()).extracting(RoomsResponse.RoomSummary::roomId).containsExactly(30L, 20L, 10L);
        assertThat(result.rooms()).extracting(RoomsResponse.RoomSummary::status).containsExactly("ACTIVE", "SELECTING", "PENDING");
        assertThat(result.rooms()).extracting(RoomsResponse.RoomSummary::currentDay).containsExactly(1, 3, 0);
        assertThat(result.rooms()).extracting(RoomsResponse.RoomSummary::totalDays).containsOnly(3);
        assertThat(result.rooms()).extracting(RoomsResponse.RoomSummary::timerType)
                .containsExactly("FINAL_SELECTION_OPENS_AT", "FINAL_SELECTION_CLOSES_AT", "ROOM_STARTS_AT");
        assertThat(result.rooms()).allSatisfy(room -> assertThat(room.profileImageUrls()).hasSize(1));
    }

    @Test
    @DisplayName("이미지 배열은 요청자를 먼저 두고 누락 이미지는 null 위치를 보존한다")
    void givenCurrentRoomsWithMixedMembersAndImages_whenGetRooms_thenPlacesSelfFirstAndPreservesNullPositions() {
        Room room = scheduledRoom(10L, NOW.minusHours(1), NOW.plusHours(2), NOW.plusHours(3), NOW.plusHours(4), NOW.plusHours(6));
        given(roomMemberReader.findByUserId(USER_ID)).willReturn(List.of(member(20L, 10L, USER_ID)));
        given(roomReader.findCurrentAll(List.of(10L), NOW)).willReturn(List.of(room));
        given(roomMemberReader.findByRoomIds(List.of(10L))).willReturn(List.of(
                member(9L, 10L, 200L), member(20L, 10L, USER_ID), member(11L, 10L, 300L)));
        given(userImageReader.getRepresentativeImageUrls(anyCollection()))
                .willReturn(Map.of(USER_ID, "raw-self", 300L, "raw-third"));
        given(s3StoragePort.createPresignedImageGetUrl("raw-self")).willReturn("signed-self");
        given(s3StoragePort.createPresignedImageGetUrl("raw-third")).willReturn("signed-third");

        RoomsResponse result = roomListService.getRooms(USER_ID);

        assertThat(result.rooms().get(0).profileImageUrls()).containsExactly("signed-self", null, "signed-third");
        then(s3StoragePort).should(never()).createPresignedImageGetUrl(null);
    }

    @Test
    @DisplayName("동일 상태 방은 타이머 목표, null 목표 마지막, room_id 순으로 정렬한다")
    void givenSameStatusRooms_whenGetRooms_thenSortsByTargetThenNullLastThenRoomId() {
        Room near = scheduledRoom(4L, NOW.minusHours(1), NOW.plusMinutes(10), NOW.plusHours(2), NOW.plusHours(3), NOW.plusHours(4));
        Room tieLowerId = scheduledRoom(2L, NOW.minusHours(1), NOW.plusMinutes(20), NOW.plusHours(2), NOW.plusHours(3), NOW.plusHours(4));
        Room tieHigherId = scheduledRoom(3L, NOW.minusHours(1), NOW.plusMinutes(20), NOW.plusHours(2), NOW.plusHours(3), NOW.plusHours(4));
        Room selectingWithTarget = scheduledRoom(6L, NOW.minusHours(5), NOW.minusHours(4), NOW.plusMinutes(30), NOW.plusHours(1), NOW.plusHours(2));
        Room afterResult = scheduledRoom(5L, NOW.minusHours(5), NOW.minusHours(4), NOW.minusHours(3), NOW.minusHours(1), NOW.plusHours(1));
        given(roomMemberReader.findByUserId(USER_ID)).willReturn(List.of(member(1L, 4L, USER_ID), member(2L, 2L, USER_ID), member(3L, 3L, USER_ID), member(4L, 6L, USER_ID), member(5L, 5L, USER_ID)));
        given(roomReader.findCurrentAll(List.of(4L, 2L, 3L, 6L, 5L), NOW)).willReturn(List.of(afterResult, tieHigherId, selectingWithTarget, near, tieLowerId));
        given(roomMemberReader.findByRoomIds(anyCollection())).willReturn(List.of(
                member(11L, 4L, USER_ID), member(12L, 2L, USER_ID), member(13L, 3L, USER_ID),
                member(14L, 6L, USER_ID), member(15L, 5L, USER_ID)));
        given(userImageReader.getRepresentativeImageUrls(anyCollection())).willReturn(Map.of());

        List<RoomsResponse.RoomSummary> rooms = roomListService.getRooms(USER_ID).rooms();

        assertThat(rooms).extracting(RoomsResponse.RoomSummary::roomId)
                .containsExactly(4L, 2L, 3L, 6L, 5L);
        assertThat(rooms.subList(3, 5)).extracting(RoomsResponse.RoomSummary::status)
                .containsOnly("SELECTING");
        assertThat(rooms.subList(3, 5)).extracting(RoomsResponse.RoomSummary::timerTargetAt)
                .containsExactly(NOW.plusMinutes(30), null);
    }

    @Test
    @DisplayName("방 이력이 없으면 빈 목록을 반환하고 후속 배치 조회를 하지 않는다")
    void givenNoMembership_whenGetRooms_thenReturnsEmptyRoomsAndSkipsBatchReads() {
        given(roomMemberReader.findByUserId(USER_ID)).willReturn(List.of());

        RoomsResponse result = roomListService.getRooms(USER_ID);

        assertThat(result.now()).isEqualTo(NOW);
        assertThat(result.rooms()).isEmpty();
        then(roomReader).shouldHaveNoInteractions();
        then(userImageReader).shouldHaveNoInteractions();
        then(s3StoragePort).shouldHaveNoInteractions();
        then(roomMemberReader).should(never()).findByRoomIds(anyCollection());
    }

    @Test
    @DisplayName("현재가 아닌 방은 멤버·이미지 배치 범위와 N+1 없는 오케스트레이션에서 제외된다")
    void givenHistoricalRooms_whenGetRooms_thenMemberAndImageBatchesReceiveOnlyCurrentRoomScope() {
        Room active = scheduledRoom(10L, NOW.minusHours(1), NOW.plusHours(1), NOW.plusHours(2), NOW.plusHours(3), NOW.plusHours(4));
        given(roomMemberReader.findByUserId(USER_ID)).willReturn(List.of(member(1L, 10L, USER_ID), member(2L, 99L, USER_ID)));
        given(roomReader.findCurrentAll(List.of(10L, 99L), NOW)).willReturn(List.of(active));
        given(roomMemberReader.findByRoomIds(List.of(10L))).willReturn(List.of(member(3L, 10L, USER_ID), member(4L, 10L, 200L)));
        given(userImageReader.getRepresentativeImageUrls(anyCollection())).willReturn(Map.of());

        roomListService.getRooms(USER_ID);

        ArgumentCaptor<Collection<Long>> roomIds = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Collection<Long>> imageUserIds = ArgumentCaptor.forClass(Collection.class);
        then(roomMemberReader).should().findByRoomIds(roomIds.capture());
        then(userImageReader).should().getRepresentativeImageUrls(imageUserIds.capture());
        then(roomMemberReader).should(never()).getByRoomId(org.mockito.ArgumentMatchers.anyLong());
        then(userImageReader).should(never()).getRepresentativeImageUrl(org.mockito.ArgumentMatchers.anyLong());
        assertThat(roomIds.getValue()).containsExactly(10L);
        assertThat(imageUserIds.getValue()).containsExactlyInAnyOrder(USER_ID, 200L);
    }

    @Test
    @DisplayName("목록 조회는 모든 파생값에 사용할 now를 Clock에서 한 번만 캡처한다")
    void givenClockThatChangesAfterFirstRead_whenGetRooms_thenUsesOnlyFirstNow() {
        Clock changingClock = org.mockito.Mockito.mock(Clock.class);
        given(changingClock.getZone()).willReturn(SEOUL);
        given(changingClock.instant()).willReturn(NOW.atZone(SEOUL).toInstant(), NOW.plusHours(1).atZone(SEOUL).toInstant());
        Room room = scheduledRoom(10L, NOW.minusHours(1), NOW.plusHours(1), NOW.plusHours(2), NOW.plusHours(3), NOW.plusHours(4));
        RoomListService service = new RoomListService(roomReader, roomMemberReader, userImageReader, s3StoragePort, changingClock);
        given(roomMemberReader.findByUserId(USER_ID)).willReturn(List.of(member(1L, 10L, USER_ID)));
        given(roomReader.findCurrentAll(List.of(10L), NOW)).willReturn(List.of(room));
        given(roomMemberReader.findByRoomIds(List.of(10L))).willReturn(List.of(member(2L, 10L, USER_ID)));
        given(userImageReader.getRepresentativeImageUrls(anyCollection())).willReturn(Map.of());

        RoomsResponse result = service.getRooms(USER_ID);

        assertThat(result.now()).isEqualTo(NOW);
        assertThat(result.rooms().get(0).status()).isEqualTo("ACTIVE");
        then(changingClock).should(times(1)).instant();
    }

    @Test
    @DisplayName("이미지 배열은 요청자를 먼저 두고 일곱 명의 실제 방 멤버 전원을 반환한다")
    void givenSevenCurrentMembers_whenGetRooms_thenReturnsSevenRequesterFirstImageSlots() {
        Room room = scheduledRoom(10L, NOW.minusHours(1), NOW.plusHours(1), NOW.plusHours(2), NOW.plusHours(3), NOW.plusHours(4));
        given(roomMemberReader.findByUserId(USER_ID)).willReturn(List.of(member(20L, 10L, USER_ID)));
        given(roomReader.findCurrentAll(List.of(10L), NOW)).willReturn(List.of(room));
        given(roomMemberReader.findByRoomIds(List.of(10L))).willReturn(List.of(
                member(20L, 10L, USER_ID), member(1L, 10L, 201L), member(2L, 10L, 202L),
                member(3L, 10L, 203L), member(4L, 10L, 204L), member(5L, 10L, 205L),
                member(6L, 10L, 206L)));
        Map<Long, String> images = new HashMap<>();
        images.put(USER_ID, "self");
        for (long userId = 201L; userId <= 206L; userId++) {
            images.put(userId, "member-" + userId);
        }
        given(userImageReader.getRepresentativeImageUrls(anyCollection())).willReturn(images);
        given(s3StoragePort.createPresignedImageGetUrl(org.mockito.ArgumentMatchers.anyString()))
                .willAnswer(invocation -> "signed-" + invocation.getArgument(0));

        List<String> urls = roomListService.getRooms(USER_ID).rooms().get(0).profileImageUrls();

        assertThat(urls).containsExactly("signed-self", "signed-member-201", "signed-member-202",
                "signed-member-203", "signed-member-204", "signed-member-205", "signed-member-206");
    }

    @Test
    @DisplayName("현재 방의 실제 멤버 행이 없으면 이미지 배열은 빈 배열이다")
    void givenCurrentRoomWithoutMemberRows_whenGetRooms_thenReturnsEmptyImageSlots() {
        Room room = scheduledRoom(10L, NOW.minusHours(1), NOW.plusHours(1), NOW.plusHours(2),
                NOW.plusHours(3), NOW.plusHours(4));
        given(roomMemberReader.findByUserId(USER_ID)).willReturn(List.of(member(20L, 10L, USER_ID)));
        given(roomReader.findCurrentAll(List.of(10L), NOW)).willReturn(List.of(room));
        given(roomMemberReader.findByRoomIds(List.of(10L))).willReturn(List.of());
        given(userImageReader.getRepresentativeImageUrls(anyCollection())).willReturn(Map.of());

        List<String> urls = roomListService.getRooms(USER_ID).rooms().get(0).profileImageUrls();

        assertThat(urls).isEmpty();
        then(s3StoragePort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("종료 방 포함 요청은 종료 경계의 방을 유지하고 취소 방은 제외한다")
    void givenEndedAndCancelledRooms_whenIncludeEnded_thenReturnsEndedAfterCurrentRooms() {
        Room active = scheduledRoom(10L, NOW.minusHours(1), NOW.plusHours(1), NOW.plusHours(2), NOW.plusHours(3), NOW.plusHours(4));
        Room ended = scheduledRoom(20L, NOW.minusDays(3), NOW.minusHours(3), NOW.minusHours(2), NOW.minusHours(1), NOW);
        given(roomMemberReader.findByUserId(USER_ID)).willReturn(List.of(member(1L, 10L, USER_ID), member(2L, 20L, USER_ID), member(3L, 30L, USER_ID)));
        given(roomReader.findCurrentAll(List.of(10L, 20L, 30L), NOW)).willReturn(List.of(active));
        given(roomReader.findEnded(List.of(10L, 20L, 30L), NOW, null, 21)).willReturn(List.of(ended));
        given(roomMemberReader.findByRoomIds(anyCollection())).willReturn(List.of(member(1L, 10L, USER_ID), member(2L, 20L, USER_ID)));
        given(userImageReader.getRepresentativeImageUrls(anyCollection())).willReturn(Map.of());

        List<RoomsResponse.RoomSummary> rooms = roomListService.getRooms(USER_ID, true).rooms();

        assertThat(rooms).extracting(RoomsResponse.RoomSummary::roomId).containsExactly(10L, 20L);
        assertThat(rooms.get(1).status()).isEqualTo("ENDED");
        assertThat(rooms.get(1).timerType()).isNull();
        assertThat(rooms.get(1).refreshAt()).isNull();
    }

    private static Room scheduledRoom(Long id, LocalDateTime starts, LocalDateTime opens,
                                      LocalDateTime closes, LocalDateTime result, LocalDateTime ends) {
        return new Room(id, RoomStatus.PENDING, starts.toLocalDate(), 3, starts, null,
                starts, opens, closes, result, ends);
    }

    private static RoomMember member(Long id, Long roomId, Long userId) {
        return new RoomMember(id, roomId, userId, "MALE", null, null, NOW);
    }
}
