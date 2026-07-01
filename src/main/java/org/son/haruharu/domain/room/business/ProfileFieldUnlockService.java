package org.son.haruharu.domain.room.business;

import org.son.haruharu.domain.profile.implement.ProfileField;
import org.son.haruharu.domain.profile.implement.ProfileFieldCatalog;
import org.son.haruharu.domain.profile.implement.ProfileFieldCatalogReader;
import org.son.haruharu.domain.profile.implement.ProfileFieldUnlock;
import org.son.haruharu.domain.profile.implement.ProfileFieldUnlockReader;
import org.son.haruharu.domain.profile.implement.ProfileFieldUnlockWriter;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.room.business.dto.ProfileFieldUnlockResponse;
import org.son.haruharu.domain.room.business.exception.InsufficientKeysException;
import org.son.haruharu.domain.room.business.exception.ProfileFieldNotYetUnlockableException;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.storage.exception.RoomMemberNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 프로필 항목 해금. 내 열쇠(room_members.point_sum)를 unlock_cost만큼 차감하고 상대 항목 해금을 기록한다.
 * Day 게이트(카탈로그 availableFromDay) 통과 후에만 해금 가능하며, 차감·기록은 한 트랜잭션.
 * 이미 해금된 항목은 멱등 처리(재차감 없음). value 도메인 unlock 서비스와 대칭.
 */
@Service
@Transactional(readOnly = true)
public class ProfileFieldUnlockService {

    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final RoomMemberWriter roomMemberWriter;
    private final UserProfileReader userProfileReader;
    private final ProfileFieldCatalogReader profileFieldCatalogReader;
    private final ProfileFieldUnlockReader profileFieldUnlockReader;
    private final ProfileFieldUnlockWriter profileFieldUnlockWriter;
    private final Clock clock;

    public ProfileFieldUnlockService(RoomReader roomReader, RoomMemberReader roomMemberReader,
                                     RoomMemberWriter roomMemberWriter, UserProfileReader userProfileReader,
                                     ProfileFieldCatalogReader profileFieldCatalogReader,
                                     ProfileFieldUnlockReader profileFieldUnlockReader,
                                     ProfileFieldUnlockWriter profileFieldUnlockWriter, Clock clock) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.roomMemberWriter = roomMemberWriter;
        this.userProfileReader = userProfileReader;
        this.profileFieldCatalogReader = profileFieldCatalogReader;
        this.profileFieldUnlockReader = profileFieldUnlockReader;
        this.profileFieldUnlockWriter = profileFieldUnlockWriter;
        this.clock = clock;
    }

    @Transactional
    public ProfileFieldUnlockResponse unlock(UUID roomId, UUID memberId, String fieldKey, UUID userId) {
        RoomMember me = roomMemberReader.getByRoomIdAndUserId(roomId, userId);
        RoomMember target = roomMemberReader.getById(memberId);
        if (!target.roomId().equals(roomId)) {
            throw new RoomMemberNotFoundException();
        }

        ProfileField field = ProfileField.fromKey(fieldKey);
        ProfileFieldCatalog catalog = profileFieldCatalogReader.getByFieldKey(fieldKey);

        // 멱등: 이미 해금했으면 Day 게이트·잔액과 무관하게 현재 값을 반환(재차감 없음).
        // Day 게이트보다 먼저 두어, 운영 중 availableFromDay를 상향해도 기해금 항목은 계속 조회된다.
        if (profileFieldUnlockReader.isUnlocked(me.id(), target.id(), fieldKey)) {
            return new ProfileFieldUnlockResponse(fieldKey, extractValue(target, field), me.pointSum());
        }

        Room room = roomReader.findById(roomId)
                .orElseThrow(RoomNotFoundException::new);
        if (!catalog.isUnlockableOn(room.currentDay())) {
            throw new ProfileFieldNotYetUnlockableException();
        }
        if (me.pointSum() < catalog.unlockCost()) {
            throw new InsufficientKeysException();
        }

        RoomMember updated = roomMemberWriter.save(me.addPoints(-catalog.unlockCost()));
        profileFieldUnlockWriter.save(
                ProfileFieldUnlock.of(me.id(), target.id(), fieldKey, LocalDateTime.now(clock)));

        return new ProfileFieldUnlockResponse(fieldKey, extractValue(target, field), updated.pointSum());
    }

    /** 성공 경로에서만 호출 — 예외 시 불필요한 프로필 조회를 방지한다. */
    private String extractValue(RoomMember target, ProfileField field) {
        return field.extract(userProfileReader.findByUserId(target.userId()).orElse(null));
    }
}
