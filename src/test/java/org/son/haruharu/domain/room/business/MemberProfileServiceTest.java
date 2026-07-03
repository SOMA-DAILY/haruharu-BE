package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.room.business.dto.MemberProfileResponse;
import org.son.haruharu.domain.room.business.dto.MemberProfileResponse.ProfileFieldView;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.exception.NotRoomMemberException;
import org.son.haruharu.domain.room.storage.exception.RoomMemberNotFoundException;
import org.son.haruharu.domain.value.implement.UserValueAnswerReader;
import org.son.haruharu.domain.value.implement.ValueQuestionReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MemberProfileServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Mock org.son.haruharu.domain.room.implement.RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock UserProfileReader userProfileReader;
    @Mock ValueQuestionReader valueQuestionReader;
    @Mock UserValueAnswerReader userValueAnswerReader;

    MemberProfileService service;

    // 2026-06-26 19:00 KST 고정 → 방날짜 2026-06-26. startedAt 앵커로 currentDay를 파생한다.
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-26T10:00:00Z"), ZONE);

    @BeforeEach
    void setUp() {
        service = new MemberProfileService(roomReader, roomMemberReader, userProfileReader,
                valueQuestionReader, userValueAnswerReader, clock);
    }

    private RoomMember member(UUID id, UUID roomId, Long userId) {
        return new RoomMember(id, roomId, userId, "FEMALE", LocalDate.of(2000, 1, 1), LocalDateTime.now());
    }

    // startedAt=방날짜(2026-06-26) 09:00에서 (currentDay-1)일 전 앵커 → currentDayAt(clock)=currentDay
    private Room room(UUID roomId, int currentDay) {
        LocalDateTime startedAt = LocalDateTime.of(2026, 6, 26, 9, 0).minusDays(currentDay - 1L);
        return new Room(roomId, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 24), 3, startedAt, null);
    }

    private UserProfile profile(Long userId) {
        return new UserProfile(UUID.randomUUID(), userId,
                "서울", "카카오", 165, "INFP", "하루", "SOMETIMES", "NONE");
    }

    private ProfileFieldView field(MemberProfileResponse res, String key) {
        return res.fields().stream().filter(f -> f.key().equals(key)).findFirst().orElseThrow();
    }

    private void stubBasics(UUID roomId, Long userId, UUID meId, UUID targetId, Long targetUserId, int currentDay) {
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(meId, roomId, userId));
        given(roomMemberReader.getById(targetId)).willReturn(member(targetId, roomId, targetUserId));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room(roomId, currentDay)));
        given(userProfileReader.findByUserId(targetUserId)).willReturn(Optional.of(profile(targetUserId)));
        given(valueQuestionReader.getAllActive()).willReturn(List.of());
        given(userValueAnswerReader.getAllByUserId(targetUserId)).willReturn(List.of());
    }

    @Test
    @DisplayName("Day1에는 닉네임·성별만 노출되고 지역·나이·직업 등은 가려진다(재화·해금 없음)")
    void givenDay1_whenGetProfile_thenOnlyNicknameAndGenderVisible() {
        Long userId = 1L;
        Long targetUserId = 2L;
        UUID roomId = UUID.randomUUID();
        UUID meId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        stubBasics(roomId, userId, meId, targetId, targetUserId, 1);

        MemberProfileResponse result = service.getMemberProfile(roomId, targetId, userId);

        assertThat(result.nickname()).isEqualTo("하루");
        assertThat(result.gender()).isEqualTo("FEMALE");

        assertThat(field(result, "location").visible()).isFalse();
        assertThat(field(result, "location").value()).isNull();
        assertThat(field(result, "age").visible()).isFalse();
        assertThat(field(result, "age").value()).isNull();
        ProfileFieldView height = field(result, "height");
        assertThat(height.visible()).isFalse();
        assertThat(height.value()).isNull();
        assertThat(height.availableFromDay()).isEqualTo(3);
    }

    @Test
    @DisplayName("Day3에는 지역·나이·직업·키·MBTI·음주·흡연이 전원에게 자동 공개된다")
    void givenDay3_whenGetProfile_thenAllFieldsVisible() {
        Long userId = 1L;
        Long targetUserId = 2L;
        UUID roomId = UUID.randomUUID();
        UUID meId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        stubBasics(roomId, userId, meId, targetId, targetUserId, 3);

        MemberProfileResponse result = service.getMemberProfile(roomId, targetId, userId);

        assertThat(field(result, "location").value()).isEqualTo("서울");
        assertThat(field(result, "affiliation").value()).isEqualTo("카카오");
        assertThat(field(result, "height").value()).isEqualTo("165");
        assertThat(field(result, "mbti").value()).isEqualTo("INFP");
        assertThat(field(result, "drinking").value()).isEqualTo("SOMETIMES");
        assertThat(field(result, "smoking").value()).isEqualTo("NONE");
        assertThat(field(result, "age").value()).isEqualTo("26");
        assertThat(result.fields()).allMatch(ProfileFieldView::visible);
    }

    @Test
    @DisplayName("본인 프로필은 Day와 무관하게 모든 항목이 공개된다")
    void givenSelf_whenGetProfile_thenAllVisibleEvenBeforeDay3() {
        Long userId = 1L;
        UUID roomId = UUID.randomUUID();
        UUID meId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(meId, roomId, userId));
        given(roomMemberReader.getById(targetId)).willReturn(member(targetId, roomId, userId)); // target==me
        given(roomReader.findById(roomId)).willReturn(Optional.of(room(roomId, 1)));
        given(userProfileReader.findByUserId(userId)).willReturn(Optional.of(profile(userId)));
        given(valueQuestionReader.getAllActive()).willReturn(List.of());
        given(userValueAnswerReader.getAllByUserId(userId)).willReturn(List.of());

        MemberProfileResponse result = service.getMemberProfile(roomId, targetId, userId);

        assertThat(field(result, "affiliation").value()).isEqualTo("카카오");
        assertThat(field(result, "age").value()).isEqualTo("26");
        assertThat(result.fields()).allMatch(ProfileFieldView::visible);
    }

    @Test
    @DisplayName("프로필 미작성·birthday 없는 멤버는 Day3여도 예외 없이 닉네임·값·나이가 null로 내려간다")
    void givenNoProfileAndNoBirthday_whenGetProfile_thenNullSafe() {
        Long userId = 1L;
        Long targetUserId = 2L;
        UUID roomId = UUID.randomUUID();
        UUID meId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(meId, roomId, userId));
        // birthday null인 대상 멤버
        given(roomMemberReader.getById(targetId)).willReturn(
                new RoomMember(targetId, roomId, targetUserId, "MALE", null, LocalDateTime.now()));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room(roomId, 3)));
        given(userProfileReader.findByUserId(targetUserId)).willReturn(Optional.empty()); // 프로필 미작성
        given(valueQuestionReader.getAllActive()).willReturn(List.of());
        given(userValueAnswerReader.getAllByUserId(targetUserId)).willReturn(List.of());

        MemberProfileResponse result = service.getMemberProfile(roomId, targetId, userId);

        assertThat(result.nickname()).isNull();
        // Day3라 visible=true지만 프로필이 없어 값은 null
        assertThat(field(result, "affiliation").visible()).isTrue();
        assertThat(field(result, "affiliation").value()).isNull();
        assertThat(field(result, "age").visible()).isTrue();
        assertThat(field(result, "age").value()).isNull();
    }

    @Test
    @DisplayName("방 멤버가 아니면 NotRoomMemberException이 발생한다")
    void givenNonMember_whenGetProfile_thenThrows() {
        Long userId = 1L;
        UUID roomId = UUID.randomUUID();
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willThrow(new NotRoomMemberException());

        assertThatThrownBy(() -> service.getMemberProfile(roomId, UUID.randomUUID(), userId))
                .isInstanceOf(NotRoomMemberException.class);
    }

    @Test
    @DisplayName("다른 방 멤버의 프로필을 조회하면 RoomMemberNotFoundException이 발생한다")
    void givenMemberInAnotherRoom_whenGetProfile_thenThrows() {
        Long userId = 1L;
        UUID roomId = UUID.randomUUID();
        UUID otherRoomId = UUID.randomUUID();
        UUID meId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(meId, roomId, userId));
        given(roomMemberReader.getById(targetId)).willReturn(member(targetId, otherRoomId, 2L));

        assertThatThrownBy(() -> service.getMemberProfile(roomId, targetId, userId))
                .isInstanceOf(RoomMemberNotFoundException.class);
    }
}
