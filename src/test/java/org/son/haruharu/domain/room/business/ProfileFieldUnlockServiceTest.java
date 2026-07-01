package org.son.haruharu.domain.room.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.profile.implement.ProfileFieldCatalog;
import org.son.haruharu.domain.profile.implement.ProfileFieldCatalogReader;
import org.son.haruharu.domain.profile.implement.ProfileFieldUnlock;
import org.son.haruharu.domain.profile.implement.ProfileFieldUnlockReader;
import org.son.haruharu.domain.profile.implement.ProfileFieldUnlockWriter;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.profile.implement.exception.InvalidProfileFieldException;
import org.son.haruharu.domain.room.business.dto.ProfileFieldUnlockResponse;
import org.son.haruharu.domain.room.business.exception.InsufficientKeysException;
import org.son.haruharu.domain.room.business.exception.ProfileFieldNotYetUnlockableException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ProfileFieldUnlockServiceTest {

    @Mock RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock RoomMemberWriter roomMemberWriter;
    @Mock UserProfileReader userProfileReader;
    @Mock ProfileFieldCatalogReader profileFieldCatalogReader;
    @Mock ProfileFieldUnlockReader profileFieldUnlockReader;
    @Mock ProfileFieldUnlockWriter profileFieldUnlockWriter;

    ProfileFieldUnlockService service;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-26T10:00:00Z"), ZoneId.of("Asia/Seoul"));

    private final UUID roomId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID targetUserId = UUID.randomUUID();
    private final UUID meId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();
    private static final String FIELD = "affiliation";

    @BeforeEach
    void setUp() {
        service = new ProfileFieldUnlockService(roomReader, roomMemberReader, roomMemberWriter, userProfileReader,
                profileFieldCatalogReader, profileFieldUnlockReader, profileFieldUnlockWriter, clock);
    }

    private RoomMember me(int pointSum) {
        return new RoomMember(meId, roomId, userId, "MALE", pointSum, LocalDateTime.now());
    }

    private RoomMember target() {
        return new RoomMember(targetId, roomId, targetUserId, "FEMALE", 0, LocalDateTime.now());
    }

    private Room room(int currentDay) {
        return new Room(roomId, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 24), 3, currentDay, LocalDateTime.now(), null);
    }

    private ProfileFieldCatalog catalog() {
        return new ProfileFieldCatalog(UUID.randomUUID(), FIELD, 10, 3, true);
    }

    private UserProfile targetProfile() {
        return new UserProfile(UUID.randomUUID(), targetUserId, "FEMALE", LocalDate.of(2000, 1, 1),
                "서울", "카카오", 165, "INFP", "하루", "SOMETIMES", "NONE", "NONE");
    }

    private void stubMembers(int myPoints) {
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(me(myPoints));
        given(roomMemberReader.getById(targetId)).willReturn(target());
    }

    @Test
    @DisplayName("Day3에 열쇠가 충분하고 미해금이면 unlock_cost만큼 차감하고 항목 값을 반환한다")
    void givenDay3EnoughKeys_whenUnlock_thenChargesAndUnlocks() {
        stubMembers(30);
        given(profileFieldCatalogReader.getByFieldKey(FIELD)).willReturn(catalog());
        given(roomReader.findById(roomId)).willReturn(Optional.of(room(3)));
        given(userProfileReader.findByUserId(targetUserId)).willReturn(Optional.of(targetProfile()));
        given(profileFieldUnlockReader.isUnlocked(meId, targetId, FIELD)).willReturn(false);
        given(roomMemberWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        ProfileFieldUnlockResponse result = service.unlock(roomId, targetId, FIELD, userId);

        assertThat(result.fieldKey()).isEqualTo(FIELD);
        assertThat(result.value()).isEqualTo("카카오");
        assertThat(result.remainingPointSum()).isEqualTo(20);
        then(profileFieldUnlockWriter).should().save(any(ProfileFieldUnlock.class));
    }

    @Test
    @DisplayName("Day3 이전이면 ProfileFieldNotYetUnlockableException이 발생하고 차감·해금이 없다")
    void givenBeforeDay3_whenUnlock_thenThrowsAndNoCharge() {
        stubMembers(30);
        given(profileFieldCatalogReader.getByFieldKey(FIELD)).willReturn(catalog());
        given(roomReader.findById(roomId)).willReturn(Optional.of(room(2)));

        assertThatThrownBy(() -> service.unlock(roomId, targetId, FIELD, userId))
                .isInstanceOf(ProfileFieldNotYetUnlockableException.class);

        then(roomMemberWriter).should(never()).save(any());
        then(profileFieldUnlockWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("열쇠가 부족하면 InsufficientKeysException이 발생하고 차감·해금이 없다")
    void givenNotEnoughKeys_whenUnlock_thenThrowsAndNoCharge() {
        stubMembers(5);
        given(profileFieldCatalogReader.getByFieldKey(FIELD)).willReturn(catalog());
        given(roomReader.findById(roomId)).willReturn(Optional.of(room(3)));
        given(profileFieldUnlockReader.isUnlocked(meId, targetId, FIELD)).willReturn(false);

        assertThatThrownBy(() -> service.unlock(roomId, targetId, FIELD, userId))
                .isInstanceOf(InsufficientKeysException.class);

        then(roomMemberWriter).should(never()).save(any());
        then(profileFieldUnlockWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("이미 해금한 항목은 재차감 없이 멱등하게 현재 상태를 반환한다")
    void givenAlreadyUnlocked_whenUnlock_thenIdempotentNoCharge() {
        stubMembers(30);
        given(profileFieldCatalogReader.getByFieldKey(FIELD)).willReturn(catalog());
        given(userProfileReader.findByUserId(targetUserId)).willReturn(Optional.of(targetProfile()));
        given(profileFieldUnlockReader.isUnlocked(meId, targetId, FIELD)).willReturn(true);

        ProfileFieldUnlockResponse result = service.unlock(roomId, targetId, FIELD, userId);

        assertThat(result.value()).isEqualTo("카카오");
        assertThat(result.remainingPointSum()).isEqualTo(30);
        then(roomMemberWriter).should(never()).save(any());
        then(profileFieldUnlockWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 프로필 항목 key면 InvalidProfileFieldException이 발생한다")
    void givenInvalidFieldKey_whenUnlock_thenThrows() {
        stubMembers(30);

        assertThatThrownBy(() -> service.unlock(roomId, targetId, "unknown", userId))
                .isInstanceOf(InvalidProfileFieldException.class);
    }
}
