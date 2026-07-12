package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.profile.implement.DrinkingStatus;
import org.son.haruharu.domain.profile.implement.SmokingStatus;
import org.son.haruharu.domain.profile.implement.UserImageReader;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.room.business.dto.MembersResponse;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.exception.NotRoomMemberException;
import org.son.haruharu.infrastructure.s3.S3StoragePort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RoomMembersServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 2026-06-26 고정 → 2000-01-01생은 26세
    private final Clock clock = Clock.fixed(
            ZonedDateTime.of(2026, 6, 26, 12, 0, 0, 0, SEOUL).toInstant(), SEOUL);

    @Mock RoomMemberReader roomMemberReader;
    @Mock UserProfileReader userProfileReader;
    @Mock UserImageReader userImageReader;
    @Mock S3StoragePort s3StoragePort;

    @BeforeEach
    void setUp() {
        // 대표 이미지 presigned 변환은 관찰 가능하도록 "?sig" 접미사를 붙인다. 이미지 없는 멤버(null)는 null 유지.
        lenient().when(userImageReader.getRepresentativeImageUrls(anyCollection())).thenReturn(Map.of());
        lenient().when(s3StoragePort.createPresignedImageGetUrl(any()))
                .thenAnswer(inv -> inv.getArgument(0) == null ? null : inv.getArgument(0) + "?sig");
    }

    private RoomMembersService service() {
        return new RoomMembersService(roomMemberReader, userProfileReader, userImageReader, s3StoragePort, clock);
    }

    private UserProfile profile(Long userId, String nickname, String location) {
        return new UserProfile(1L, userId,
                location, "ABC대학교", 180, "INTJ", nickname, DrinkingStatus.SOMETIMES, SmokingStatus.NONE);
    }

    private RoomMember member(Long roomId, Long userId) {
        return new RoomMember(900L, roomId, userId, "MALE", LocalDate.of(2000, 1, 1),
                LocalDateTime.now(clock));
    }

    @Test
    @DisplayName("목록은 닉네임·성별·지역·나이만 담고 Day 게이트 항목은 포함하지 않는다")
    void givenMember_whenGetMembers_thenBasicsOnly() {
        Long userId = 1L;
        Long roomId = 10L;
        RoomMember me = member(roomId, userId);

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(me);
        given(roomMemberReader.getByRoomId(roomId)).willReturn(List.of(me));
        given(userProfileReader.findByUserIdIn(List.of(userId)))
                .willReturn(Map.of(userId, profile(userId, "철수", "서울")));

        MembersResponse result = service().getMembers(roomId, userId);

        MembersResponse.MemberView view = result.members().get(0);
        assertThat(view.me()).isTrue();
        assertThat(view.nickname()).isEqualTo("철수");
        assertThat(view.location()).isEqualTo("서울");
        assertThat(view.age()).isEqualTo(26);
    }

    @Test
    @DisplayName("멤버가 여러 명이어도 프로필은 배치 조회 1회로 해결하고 건별 조회는 하지 않는다")
    void givenMultipleMembers_whenGetMembers_thenLoadsProfilesInSingleBatch() {
        Long myUserId = 1L;
        Long otherUserId = 2L;
        Long roomId = 11L;
        RoomMember me = member(roomId, myUserId);
        RoomMember other = member(roomId, otherUserId);

        given(roomMemberReader.getByRoomIdAndUserId(roomId, myUserId)).willReturn(me);
        given(roomMemberReader.getByRoomId(roomId)).willReturn(List.of(me, other));
        given(userProfileReader.findByUserIdIn(anyCollection()))
                .willReturn(Map.of(
                        myUserId, profile(myUserId, "철수", "서울"),
                        otherUserId, profile(otherUserId, "영희", "부산")));

        MembersResponse result = service().getMembers(roomId, myUserId);

        assertThat(result.members()).extracting(MembersResponse.MemberView::nickname)
                .containsExactly("철수", "영희");
        assertThat(result.members()).extracting(MembersResponse.MemberView::location)
                .containsExactly("서울", "부산");
        then(userProfileReader).should().findByUserIdIn(anyCollection());
        then(userProfileReader).should(never()).findByUserId(any());
    }

    @Test
    @DisplayName("프로필이 없고 생년월일 스냅샷도 없으면 닉네임·지역·나이가 null로 내려간다")
    void givenMemberWithoutProfile_whenGetMembers_thenNullFields() {
        Long userId = 1L;
        Long roomId = 12L;
        // 나이는 프로필이 아닌 RoomMember.birthday(본인인증 스냅샷)에서 온다 → birthday null이면 나이도 null
        RoomMember me = new RoomMember(910L, roomId, userId, "MALE", null, LocalDateTime.now(clock));

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(me);
        given(roomMemberReader.getByRoomId(roomId)).willReturn(List.of(me));
        given(userProfileReader.findByUserIdIn(List.of(userId))).willReturn(Map.of());

        MembersResponse result = service().getMembers(roomId, userId);

        MembersResponse.MemberView view = result.members().get(0);
        assertThat(view.nickname()).isNull();
        assertThat(view.location()).isNull();
        assertThat(view.age()).isNull();
        assertThat(view.gender()).isEqualTo("MALE");
    }

    @Test
    @DisplayName("멤버 목록에 신고용 user_id와 presigned 대표 이미지 URL이 포함된다")
    void givenMembersWithImages_whenGetMembers_thenIncludesUserIdAndPresignedImageUrl() {
        Long myUserId = 1L;
        Long otherUserId = 2L;
        Long roomId = 14L;
        RoomMember me = member(roomId, myUserId);
        RoomMember other = member(roomId, otherUserId);

        given(roomMemberReader.getByRoomIdAndUserId(roomId, myUserId)).willReturn(me);
        given(roomMemberReader.getByRoomId(roomId)).willReturn(List.of(me, other));
        given(userProfileReader.findByUserIdIn(anyCollection())).willReturn(Map.of());
        given(userImageReader.getRepresentativeImageUrls(anyCollection()))
                .willReturn(Map.of(myUserId, "https://cdn/me.jpg", otherUserId, "https://cdn/other.jpg"));

        MembersResponse result = service().getMembers(roomId, myUserId);

        assertThat(result.members()).extracting(MembersResponse.MemberView::userId)
                .containsExactly(myUserId, otherUserId);
        assertThat(result.members()).extracting(MembersResponse.MemberView::profileImageUrl)
                .containsExactly("https://cdn/me.jpg?sig", "https://cdn/other.jpg?sig");
    }

    @Test
    @DisplayName("대표 이미지는 유저별 단건 조회 없이 배치 1회로 조회한다")
    void givenMultipleMembers_whenGetMembers_thenLoadsImagesInSingleBatch() {
        Long myUserId = 1L;
        Long roomId = 15L;
        RoomMember me = member(roomId, myUserId);

        given(roomMemberReader.getByRoomIdAndUserId(roomId, myUserId)).willReturn(me);
        given(roomMemberReader.getByRoomId(roomId)).willReturn(List.of(me, member(roomId, 2L)));
        given(userProfileReader.findByUserIdIn(anyCollection())).willReturn(Map.of());

        service().getMembers(roomId, myUserId);

        then(userImageReader).should().getRepresentativeImageUrls(anyCollection());
        then(userImageReader).should(never()).getRepresentativeImageUrl(any());
    }

    @Test
    @DisplayName("대표 이미지가 없는 멤버는 profile_image_url이 null로 내려간다")
    void givenMemberWithoutImage_whenGetMembers_thenNullImageUrl() {
        Long userId = 1L;
        Long roomId = 16L;
        RoomMember me = member(roomId, userId);

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(me);
        given(roomMemberReader.getByRoomId(roomId)).willReturn(List.of(me));
        given(userProfileReader.findByUserIdIn(anyCollection())).willReturn(Map.of());
        given(userImageReader.getRepresentativeImageUrls(anyCollection())).willReturn(Map.of());

        MembersResponse result = service().getMembers(roomId, userId);

        assertThat(result.members().get(0).profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("방 멤버가 아니면 NotRoomMemberException이 발생한다")
    void givenNonMember_whenGetMembers_thenThrows() {
        Long userId = 1L;
        Long roomId = 13L;
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId))
            .willThrow(new NotRoomMemberException());

        assertThatThrownBy(() -> service().getMembers(roomId, userId))
            .isInstanceOf(NotRoomMemberException.class);
    }
}
