package org.son.haruharu.admin.domain.room.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.admin.common.PageResponse;
import org.son.haruharu.admin.domain.room.business.dto.AdminRoomDetailResponse;
import org.son.haruharu.admin.domain.room.business.dto.AdminRoomSummary;
import org.son.haruharu.admin.domain.room.implement.AdminRoomReader;
import org.son.haruharu.admin.domain.room.storage.exception.AdminRoomNotFoundException;
import org.son.haruharu.domain.profile.implement.UserImageReader;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.infrastructure.s3.S3StoragePort;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class AdminRoomServiceTest {

    @Mock AdminRoomReader adminRoomReader;
    @Mock UserImageReader userImageReader;
    @Mock S3StoragePort media;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-10T03:00:00Z"), ZoneOffset.UTC);

    AdminRoomService service;

    @BeforeEach
    void setUp() {
        service =
                new AdminRoomService(
                        adminRoomReader,
                        clock,
                        userImageReader,
                        media,
                        org.mockito.Mockito.mock(
                                org.son.haruharu.admin.domain.room.implement
                                        .AdminRoomOrganizationManager.class));
    }

    private Room room(Long id, RoomStatus status, LocalDateTime startedAt) {
        return new Room(id, status, LocalDate.of(2026, 7, 9), 3, startedAt, null);
    }

    private RoomMember member(Long id, Long roomId, String gender) {
        return new RoomMember(
                id,
                roomId,
                id + 900,
                gender,
                null,
                LocalDate.of(2000, 1, 1),
                LocalDateTime.now(clock));
    }

    @Test
    @DisplayName("방 목록은 성비·참여자 수·일차와 페이지 메타를 담아 반환한다")
    void getRooms_returnsSummariesWithGenderAndPageMeta() {
        Room active = room(1L, RoomStatus.ACTIVE, LocalDateTime.now(clock));
        Room pending = room(2L, RoomStatus.PENDING, null);
        given(adminRoomReader.findRooms(null, null, 0, 20)).willReturn(List.of(active, pending));
        given(adminRoomReader.countRooms(null, null)).willReturn(2L);
        given(adminRoomReader.membersOf(List.of(1L, 2L)))
                .willReturn(
                        List.of(
                                member(10L, 1L, "FEMALE"),
                                member(11L, 1L, "MALE"),
                                member(12L, 2L, "FEMALE")));

        PageResponse<AdminRoomSummary> result = service.getRooms(null, null, 0, 20);

        assertThat(result.content()).hasSize(2);
        AdminRoomSummary first = result.content().get(0);
        assertThat(first.roomId()).isEqualTo(1L);
        assertThat(first.memberCount()).isEqualTo(2);
        assertThat(first.femaleCount()).isEqualTo(1);
        assertThat(first.maleCount()).isEqualTo(1);
        assertThat(first.currentDay()).isGreaterThanOrEqualTo(1); // ACTIVE
        assertThat(result.content().get(1).currentDay()).isZero(); // PENDING
        assertThat(result.page().totalElements()).isEqualTo(2L);
        assertThat(result.page().totalPages()).isEqualTo(1);
        assertThat(result.page().page()).isZero();
    }

    @Test
    @DisplayName("페이지 크기는 최대 100으로 제한된다")
    void getRooms_clampsPageSize() {
        given(adminRoomReader.findRooms(null, null, 0, 100)).willReturn(List.of());
        given(adminRoomReader.countRooms(null, null)).willReturn(0L);
        given(adminRoomReader.membersOf(List.<Long>of())).willReturn(List.of());

        PageResponse<AdminRoomSummary> result = service.getRooms(null, null, 0, 999);

        assertThat(result.page().size()).isEqualTo(100);
    }

    @Test
    @DisplayName("방 상세의 참여자에 실명(닉네임)·가명·나이(생년 파생)·지역이 채워지고, 없는 값은 null이다")
    void getRoom_enrichesMembersWithProfileAndPseudonym() {
        given(adminRoomReader.getById(1L))
                .willReturn(room(1L, RoomStatus.ACTIVE, LocalDateTime.now(clock)));
        // 910은 가명 배정됨, 911은 가명 미배정(null)
        given(adminRoomReader.membersOf(1L))
                .willReturn(
                        List.of(
                                member(10L, 1L, "FEMALE").withPseudonym("유진"),
                                member(11L, 1L, "MALE")));
        // 910만 프로필 존재, 911은 없음
        given(adminRoomReader.profilesOf(List.of(910L, 911L)))
                .willReturn(
                        List.of(
                                new UserProfile(
                                        1L, 910L, "서울", null, null, null, "여울", null, null, null)));

        AdminRoomDetailResponse result = service.getRoom(1L);

        assertThat(result.members()).hasSize(2);
        assertThat(result.femaleCount()).isEqualTo(1);
        assertThat(result.maleCount()).isEqualTo(1);
        AdminRoomDetailResponse.MemberView m0 = result.members().get(0);
        assertThat(m0.userId()).isEqualTo(910L);
        assertThat(m0.nickname()).isEqualTo("여울"); // 실명
        assertThat(m0.pseudonym()).isEqualTo("유진"); // 가명
        assertThat(m0.region()).isEqualTo("서울");
        assertThat(m0.age()).isEqualTo(26); // 2000-01-01 ~ 2026-07-10
        AdminRoomDetailResponse.MemberView m1 = result.members().get(1);
        assertThat(m1.nickname()).isNull();
        assertThat(m1.pseudonym()).isNull(); // 가명 미배정
        assertThat(m1.region()).isNull();
    }

    @Test
    @DisplayName("대표사진을 일괄 조회하고 사진이 있는 참여자에게만 기존 원본 URL 발급을 적용한다")
    void givenMemberImages_whenGetRoom_thenResolvesExistingImagesOnly() {
        given(adminRoomReader.getById(1L))
                .willReturn(room(1L, RoomStatus.ACTIVE, LocalDateTime.now(clock)));
        given(adminRoomReader.membersOf(1L))
                .willReturn(List.of(member(10L, 1L, "FEMALE"), member(11L, 1L, "MALE")));
        given(userImageReader.getRepresentativeImageUrls(List.of(910L, 911L)))
                .willReturn(Map.of(910L, "https://example.invalid/original.jpg"));
        given(media.createPresignedImageGetUrl("https://example.invalid/original.jpg"))
                .willReturn("https://example.invalid/original.jpg?signed=test-only");

        AdminRoomDetailResponse result = service.getRoom(1L);

        assertThat(result.members().get(0).profileImageUrl())
                .isEqualTo("https://example.invalid/original.jpg?signed=test-only");
        assertThat(result.members().get(1).profileImageUrl()).isNull();
        verify(userImageReader).getRepresentativeImageUrls(List.of(910L, 911L));
        verify(media).createPresignedImageGetUrl("https://example.invalid/original.jpg");
        verifyNoMoreInteractions(userImageReader, media);
    }

    @Test
    @DisplayName("없는 방 상세 조회 시 AdminRoomNotFoundException(404)")
    void getRoom_whenNotFound_throws() {
        given(adminRoomReader.getById(99L)).willThrow(new AdminRoomNotFoundException());

        assertThatThrownBy(() -> service.getRoom(99L))
                .isInstanceOf(AdminRoomNotFoundException.class);
    }
}
