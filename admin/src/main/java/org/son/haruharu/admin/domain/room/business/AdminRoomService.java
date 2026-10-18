package org.son.haruharu.admin.domain.room.business;

import org.son.haruharu.admin.common.PageResponse;
import org.son.haruharu.admin.domain.room.business.dto.AdminRoomDetailResponse;
import org.son.haruharu.admin.domain.room.business.dto.AdminRoomSummary;
import org.son.haruharu.admin.domain.room.implement.AdminRoomReader;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomComposition;
import org.son.haruharu.domain.room.implement.RoomEntryPath;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 어드민 세션(방) 조회. 목록(필터·페이지네이션·성비)·상세(참여자). */
@Service
@Transactional(readOnly = true)
public class AdminRoomService {

    private static final String GENDER_FEMALE = "FEMALE";
    private static final String GENDER_MALE = "MALE";
    private static final int MAX_PAGE_SIZE = 100;

    private final AdminRoomReader adminRoomReader;
    private final Clock clock;

    public AdminRoomService(AdminRoomReader adminRoomReader, Clock clock) {
        this.adminRoomReader = adminRoomReader;
        this.clock = clock;
    }

    public PageResponse<AdminRoomSummary> getRooms(RoomStatus status, LocalDate scheduledDate, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        List<Room> rooms = adminRoomReader.findRooms(status, scheduledDate, safePage * safeSize, safeSize);
        long total = adminRoomReader.countRooms(status, scheduledDate);

        Map<Long, List<RoomMember>> membersByRoom = adminRoomReader
                .membersOf(rooms.stream().map(Room::id).toList()).stream()
                .collect(Collectors.groupingBy(RoomMember::roomId));

        LocalDateTime now = LocalDateTime.now(clock);
        List<AdminRoomSummary> content = rooms.stream()
                .map(room -> toSummary(room, membersByRoom.getOrDefault(room.id(), List.of()), now))
                .toList();
        return PageResponse.of(content, safePage, safeSize, total);
    }

    public AdminRoomDetailResponse getRoom(Long roomId) {
        Room room = adminRoomReader.getById(roomId);
        List<RoomMember> members = adminRoomReader.membersOf(roomId);
        int currentDay = room.currentDayAt(LocalDateTime.now(clock));

        // 참여자 프로필(닉네임·지역)을 일괄 조회해 조인, 나이는 birthday에서 파생.
        Map<Long, UserProfile> profilesByUserId = adminRoomReader
                .profilesOf(members.stream().map(RoomMember::userId).toList()).stream()
                .collect(Collectors.toMap(UserProfile::userId, Function.identity(), (a, b) -> a));
        LocalDate today = LocalDate.now(clock);
        // 본인 확인 여부(#513): 본인인증 행이 있으면 확인 완료(UserService 와 같은 기준). 한 번에 조회한다.
        Set<Long> verifiedUserIds = adminRoomReader.identityVerifiedUserIdsOf(
                members.stream().map(RoomMember::userId).toList());

        List<AdminRoomDetailResponse.MemberView> memberViews = members.stream()
                .map(m -> toMemberView(m, profilesByUserId.get(m.userId()), today,
                        verifiedUserIds.contains(m.userId())))
                .toList();
        RoomComposition composition = room.composition();
        return new AdminRoomDetailResponse(
                // 표시 status 는 파생값 — 저장 컬럼은 reconciler 가 맞추는 materialized 값이라 배치 지연에 노출된다(#320).
                room.id(), room.statusAt(LocalDateTime.now(clock)), room.scheduledDate(), room.totalDays(), currentDay,
                startedAtOf(room), room.endedAt(), members.size(),
                countGender(members, GENDER_FEMALE), countGender(members, GENDER_MALE), memberViews,
                room.startsAt(), room.finalSelectionOpensAt(), room.endsAt(),
                composition.cohortId(), composition.name(), composition.maleCapacity(), composition.femaleCapacity(),
                (int) members.stream().filter(m -> m.entryPath() == RoomEntryPath.INVITE).count());
    }

    private AdminRoomDetailResponse.MemberView toMemberView(RoomMember member, UserProfile profile, LocalDate today,
                                                            boolean identityVerified) {
        Integer age = member.birthday() == null ? null : Period.between(member.birthday(), today).getYears();
        String nickname = profile == null ? null : profile.nickname();
        String region = profile == null ? null : profile.location();
        return new AdminRoomDetailResponse.MemberView(
                member.id(), member.userId(), member.gender(), member.joinedAt(),
                nickname, member.pseudonym(), age, region,
                member.entryPath(), member.applicationId(), identityVerified);
    }

    private AdminRoomSummary toSummary(Room room, List<RoomMember> members, LocalDateTime now) {
        return new AdminRoomSummary(
                room.id(), room.statusAt(now), room.scheduledDate(), room.currentDayAt(now),
                members.size(), countGender(members, GENDER_FEMALE), countGender(members, GENDER_MALE),
                startedAtOf(room));
    }

    /** 새 모델 방은 전이 전까지 startedAt 이 비어 있으므로 예정 시각(starts_at)을 노출한다(#320). */
    private LocalDateTime startedAtOf(Room room) {
        return room.startsAt() != null ? room.startsAt() : room.startedAt();
    }

    private int countGender(List<RoomMember> members, String gender) {
        return (int) members.stream().filter(m -> gender.equals(m.gender())).count();
    }
}
