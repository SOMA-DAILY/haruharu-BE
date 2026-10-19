package org.son.haruharu.admin.domain.room.implement;

import org.son.haruharu.admin.domain.room.storage.AdminRoomQueryRepository;
import org.son.haruharu.admin.domain.room.storage.exception.AdminRoomNotFoundException;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** 어드민 세션(방) 조회 협력자. 트랜잭션 경계는 Service가 갖는다. */
@Component
public class AdminRoomReader {

    private final AdminRoomQueryRepository adminRoomQueryRepository;

    public AdminRoomReader(AdminRoomQueryRepository adminRoomQueryRepository) {
        this.adminRoomQueryRepository = adminRoomQueryRepository;
    }

    public List<Room> findRooms(RoomStatus status, LocalDate scheduledDate, int offset, int limit) {
        return findRooms(status, scheduledDate, null, offset, limit);
    }

    public List<Room> findRooms(RoomStatus status, LocalDate scheduledDate, Boolean isTest, int offset, int limit) {
        return adminRoomQueryRepository.findRooms(status, scheduledDate, isTest, offset, limit);
    }

    public long countRooms(RoomStatus status, LocalDate scheduledDate) {
        return countRooms(status, scheduledDate, null);
    }

    public long countRooms(RoomStatus status, LocalDate scheduledDate, Boolean isTest) {
        return adminRoomQueryRepository.countRooms(status, scheduledDate, isTest);
    }

    public Room getById(Long roomId) {
        return adminRoomQueryRepository.findRoomById(roomId)
                .orElseThrow(AdminRoomNotFoundException::new);
    }

    public void requireExists(Long roomId) {
        if (!adminRoomQueryRepository.existsRoomById(roomId)) {
            throw new AdminRoomNotFoundException();
        }
    }

    public List<RoomMember> membersOf(Long roomId) {
        return adminRoomQueryRepository.findMembersByRoomId(roomId);
    }

    public List<RoomMember> membersOf(List<Long> roomIds) {
        return adminRoomQueryRepository.findMembersByRoomIds(roomIds);
    }

    public List<UserProfile> profilesOf(List<Long> userIds) {
        return adminRoomQueryRepository.findProfilesByUserIds(userIds);
    }

    /** 본인인증(PASS)을 마친 유저 id(#513). 탈퇴로 지운 본인인증 행은 soft-delete 필터로 빠진다. */
    public Set<Long> identityVerifiedUserIdsOf(List<Long> userIds) {
        return Set.copyOf(adminRoomQueryRepository.findIdentityVerifiedUserIds(userIds));
    }
}
