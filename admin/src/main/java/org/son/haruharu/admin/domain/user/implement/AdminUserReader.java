package org.son.haruharu.admin.domain.user.implement;

import org.son.haruharu.admin.domain.user.storage.AdminUserQueryRepository;
import org.son.haruharu.admin.domain.user.storage.exception.AdminUserNotFoundException;
import org.son.haruharu.domain.ticket.implement.TicketWallet;
import org.son.haruharu.domain.payment.implement.IapPurchase;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.springframework.stereotype.Component;

import org.son.haruharu.domain.room.implement.FinalSelection;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 어드민 유저 조회 협력자. 트랜잭션 경계는 Service가 갖는다. */
@Component
public class AdminUserReader {

    private final AdminUserQueryRepository adminUserQueryRepository;

    public AdminUserReader(AdminUserQueryRepository adminUserQueryRepository) {
        this.adminUserQueryRepository = adminUserQueryRepository;
    }

    public List<UserWithProfile> findUsers(UserStatus status, String keyword, int offset, int limit) {
        return adminUserQueryRepository.findUsers(status, keyword, offset, limit);
    }

    public long countUsers(UserStatus status, String keyword) {
        return adminUserQueryRepository.countUsers(status, keyword);
    }

    public User getById(Long userId) {
        return adminUserQueryRepository.findUser(userId)
                .orElseThrow(AdminUserNotFoundException::new);
    }

    public UserProfile profileOf(Long userId) {
        return adminUserQueryRepository.findProfile(userId).orElse(null);
    }

    public TicketWallet walletOf(Long userId) {
        return adminUserQueryRepository.findWallet(userId).orElse(null);
    }

    public List<IapPurchase> purchasesOf(Long userId) {
        return adminUserQueryRepository.findPurchases(userId);
    }

    public List<UserParticipation> participationsOf(Long userId) {
        return adminUserQueryRepository.findParticipations(userId);
    }

    /**
     * 방 참여 이력(#508). 참여한 방들의 멤버·선택·매칭·활동 수를 방 수와 무관하게 쿼리 몇 번으로 모아 조립한다.
     */
    public List<UserRoomHistory> roomHistoryOf(Long userId) {
        List<Map.Entry<RoomMember, Room>> memberships = adminUserQueryRepository.findMemberships(userId);
        Set<Long> roomIds = memberships.stream().map(e -> e.getValue().id()).collect(Collectors.toSet());
        Set<Long> myMemberIds = memberships.stream().map(e -> e.getKey().id()).collect(Collectors.toSet());

        Map<Long, List<RoomMember>> membersByRoom = adminUserQueryRepository.findRoomMembers(roomIds).stream()
                .collect(Collectors.groupingBy(RoomMember::roomId));
        List<FinalSelection> selections = adminUserQueryRepository.findSelections(
                membersByRoom.values().stream().flatMap(List::stream).map(RoomMember::id).toList());
        Set<String> matchedPairs = adminUserQueryRepository.findMatches(myMemberIds).stream()
                .map(m -> UserRoomHistory.pairKey(m.memberAId(), m.memberBId()))
                .collect(Collectors.toSet());
        Map<Long, Long> videos = adminUserQueryRepository.countVideosByMember(myMemberIds);
        Map<Long, Long> chats = adminUserQueryRepository.countGroupChatsByRoom(userId, roomIds);

        return memberships.stream().map(e -> {
            RoomMember me = e.getKey();
            Room room = e.getValue();
            List<RoomMember> roomMembers = membersByRoom.getOrDefault(room.id(), List.of(me));
            return UserRoomHistory.of(room, me, roomMembers, selections, matchedPairs,
                    videos.getOrDefault(me.id(), 0L), chats.getOrDefault(room.id(), 0L));
        }).toList();
    }
}
