package org.son.haruharu.domain.room.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.profile.implement.ProfileFieldCatalog;
import org.son.haruharu.domain.profile.implement.ProfileFieldCatalogReader;
import org.son.haruharu.domain.profile.implement.ProfileFieldUnlockReader;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.room.business.dto.MemberProfileResponse;
import org.son.haruharu.domain.room.business.dto.MemberProfileResponse.UnlockableFieldView;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.exception.NotRoomMemberException;
import org.son.haruharu.domain.room.storage.exception.RoomMemberNotFoundException;
import org.son.haruharu.domain.value.implement.UserValueAnswerReader;
import org.son.haruharu.domain.value.implement.ValueAnswerUnlockReader;
import org.son.haruharu.domain.value.implement.ValueQuestionReader;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    @Mock ValueAnswerUnlockReader valueAnswerUnlockReader;
    @Mock ProfileFieldCatalogReader profileFieldCatalogReader;
    @Mock ProfileFieldUnlockReader profileFieldUnlockReader;

    MemberProfileService service;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-26T10:00:00Z"), ZONE);

    @BeforeEach
    void setUp() {
        service = new MemberProfileService(roomReader, roomMemberReader, userProfileReader,
                valueQuestionReader, userValueAnswerReader, valueAnswerUnlockReader,
                profileFieldCatalogReader, profileFieldUnlockReader, clock);
    }

    private RoomMember member(UUID id, UUID roomId, UUID userId) {
        return new RoomMember(id, roomId, userId, "FEMALE", LocalDate.of(2000, 1, 1), 30, LocalDateTime.now());
    }

    private Room room(UUID roomId, int currentDay) {
        return new Room(roomId, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 24), 3, currentDay,
                LocalDateTime.now(), null);
    }

    private UserProfile profile(UUID userId) {
        return new UserProfile(UUID.randomUUID(), userId,
                "서울", "카카오", 165, "INFP", "하루", "SOMETIMES", "NONE");
    }

    private List<ProfileFieldCatalog> catalog() {
        return List.of(
                new ProfileFieldCatalog(UUID.randomUUID(), "affiliation", 10, 3, true),
                new ProfileFieldCatalog(UUID.randomUUID(), "height", 15, 3, true),
                new ProfileFieldCatalog(UUID.randomUUID(), "mbti", 5, 3, true));
    }

    private UnlockableFieldView field(MemberProfileResponse res, String key) {
        return res.unlockableFields().stream().filter(f -> f.fieldKey().equals(key)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Day3 이전에는 해금 대상 항목이 unlockable=false·미해금이고 닉네임·지역·나이는 노출된다")
    void givenBeforeDay3_whenGetProfile_thenFieldsNotUnlockable() {
        UUID userId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID meId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(meId, roomId, userId));
        given(roomMemberReader.getById(targetId)).willReturn(member(targetId, roomId, targetUserId));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room(roomId, 1)));
        given(userProfileReader.findByUserId(targetUserId)).willReturn(Optional.of(profile(targetUserId)));
        given(profileFieldCatalogReader.getAllActive()).willReturn(catalog());
        given(profileFieldUnlockReader.getUnlockedFieldKeys(meId, targetId)).willReturn(List.of());
        given(valueQuestionReader.getAllActive()).willReturn(List.of());
        given(userValueAnswerReader.getAllByUserId(targetUserId)).willReturn(List.of());
        given(valueAnswerUnlockReader.getUnlockedAnswerIds(meId)).willReturn(List.of());

        MemberProfileResponse result = service.getMemberProfile(roomId, targetId, userId);

        assertThat(result.nickname()).isEqualTo("하루");
        assertThat(result.location()).isEqualTo("서울");
        assertThat(result.age()).isEqualTo(26);
        UnlockableFieldView height = field(result, "height");
        assertThat(height.unlockable()).isFalse();
        assertThat(height.unlocked()).isFalse();
        assertThat(height.value()).isNull();
        assertThat(height.unlockCost()).isEqualTo(15);
        assertThat(height.availableFromDay()).isEqualTo(3);
    }

    @Test
    @DisplayName("Day3에 미해금 항목은 unlockable=true지만 값은 가려지고, 해금한 항목만 값이 보인다")
    void givenDay3_whenGetProfile_thenUnlockableButValueGatedByUnlock() {
        UUID userId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID meId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(meId, roomId, userId));
        given(roomMemberReader.getById(targetId)).willReturn(member(targetId, roomId, targetUserId));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room(roomId, 3)));
        given(userProfileReader.findByUserId(targetUserId)).willReturn(Optional.of(profile(targetUserId)));
        given(profileFieldCatalogReader.getAllActive()).willReturn(catalog());
        given(profileFieldUnlockReader.getUnlockedFieldKeys(meId, targetId)).willReturn(List.of("affiliation"));
        given(valueQuestionReader.getAllActive()).willReturn(List.of());
        given(userValueAnswerReader.getAllByUserId(targetUserId)).willReturn(List.of());
        given(valueAnswerUnlockReader.getUnlockedAnswerIds(meId)).willReturn(List.of());

        MemberProfileResponse result = service.getMemberProfile(roomId, targetId, userId);

        UnlockableFieldView affiliation = field(result, "affiliation");
        assertThat(affiliation.unlockable()).isTrue();
        assertThat(affiliation.unlocked()).isTrue();
        assertThat(affiliation.value()).isEqualTo("카카오");

        UnlockableFieldView mbti = field(result, "mbti");
        assertThat(mbti.unlockable()).isTrue();
        assertThat(mbti.unlocked()).isFalse();
        assertThat(mbti.value()).isNull();
    }

    @Test
    @DisplayName("방 멤버가 아니면 NotRoomMemberException이 발생한다")
    void givenNonMember_whenGetProfile_thenThrows() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willThrow(new NotRoomMemberException());

        assertThatThrownBy(() -> service.getMemberProfile(roomId, UUID.randomUUID(), userId))
                .isInstanceOf(NotRoomMemberException.class);
    }

    @Test
    @DisplayName("다른 방 멤버의 프로필을 조회하면 RoomMemberNotFoundException이 발생한다")
    void givenMemberInAnotherRoom_whenGetProfile_thenThrows() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID otherRoomId = UUID.randomUUID();
        UUID meId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(meId, roomId, userId));
        given(roomMemberReader.getById(targetId)).willReturn(member(targetId, otherRoomId, UUID.randomUUID()));

        assertThatThrownBy(() -> service.getMemberProfile(roomId, targetId, userId))
                .isInstanceOf(RoomMemberNotFoundException.class);
    }
}
