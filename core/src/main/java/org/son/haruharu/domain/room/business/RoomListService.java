package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.son.haruharu.domain.profile.implement.UserImageReader;
import org.son.haruharu.domain.room.business.dto.RoomListTimer;
import org.son.haruharu.domain.room.business.dto.RoomsResponse;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoomListService {

    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final UserImageReader userImageReader;
    private final S3StoragePort s3StoragePort;
    private final Clock clock;

    public RoomListService(RoomReader roomReader, RoomMemberReader roomMemberReader,
                           UserImageReader userImageReader, S3StoragePort s3StoragePort, Clock clock) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.userImageReader = userImageReader;
        this.s3StoragePort = s3StoragePort;
        this.clock = clock;
    }

    public RoomsResponse getRooms(Long userId) {
        return getRooms(userId, false);
    }

    public RoomsResponse getRooms(Long userId, boolean includeEnded) {
        return getRooms(userId, includeEnded, null);
    }

    public RoomsResponse getRooms(Long userId, boolean includeEnded, Long endedBefore) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> candidateRoomIds = roomMemberReader.findByUserId(userId).stream()
                .map(RoomMember::roomId)
                .distinct()
                .toList();
        if (candidateRoomIds.isEmpty()) {
            return new RoomsResponse(now, List.of());
        }

        List<Room> currentRooms = new ArrayList<>(includeEnded && endedBefore != null
                ? List.of() : roomReader.findCurrentAll(candidateRoomIds, now));
        Long nextEndedCursor = null;
        if (includeEnded) {
            List<Room> ended = roomReader.findEnded(candidateRoomIds, now, endedBefore, 21);
            if (ended.size() > 20) nextEndedCursor = ended.get(19).id();
            currentRooms.addAll(ended.stream().limit(20).toList());
        }
        if (currentRooms.isEmpty()) {
            return new RoomsResponse(now, List.of());
        }

        List<Long> currentRoomIds = currentRooms.stream().map(Room::id).toList();
        List<RoomMember> currentMembers = roomMemberReader.findByRoomIds(currentRoomIds);
        Map<Long, List<RoomMember>> membersByRoomId = currentMembers.stream()
                .collect(Collectors.groupingBy(RoomMember::roomId));
        Collection<Long> currentUserIds = currentMembers.stream()
                .map(RoomMember::userId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> representativeUrls = userImageReader.getRepresentativeImageUrls(currentUserIds);

        List<RoomsResponse.RoomSummary> summaries = currentRooms.stream()
                .map(room -> summary(room, userId, now, membersByRoomId.getOrDefault(room.id(), List.of()), representativeUrls))
                .sorted(roomSummaryComparator())
                .toList();
        return new RoomsResponse(now, summaries, nextEndedCursor);
    }

    private RoomsResponse.RoomSummary summary(Room room, Long requesterId, LocalDateTime now,
                                              List<RoomMember> members, Map<Long, String> representativeUrls) {
        RoomListTimer timer = RoomListTimer.from(room, now);
        List<RoomMember> orderedMembers = members.stream()
                .sorted(Comparator.comparing((RoomMember member) -> !Objects.equals(member.userId(), requesterId))
                        .thenComparing(RoomMember::id, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<String> profileImageUrls = new ArrayList<>(orderedMembers.size());
        for (RoomMember member : orderedMembers) {
            String rawUrl = representativeUrls.get(member.userId());
            profileImageUrls.add(rawUrl == null ? null : s3StoragePort.createPresignedImageGetUrl(rawUrl));
        }
        return new RoomsResponse.RoomSummary(room.id(), room.statusAt(now).name(), room.currentDayAt(now),
                room.totalDays(), profileImageUrls,
                timer.timerType() == null ? null : timer.timerType().name(),
                timer.timerTargetAt(), timer.refreshAt());
    }

    private Comparator<RoomsResponse.RoomSummary> roomSummaryComparator() {
        return Comparator.comparingInt((RoomsResponse.RoomSummary room) -> statusRank(room.status()))
                .thenComparing(RoomsResponse.RoomSummary::timerTargetAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(room -> "ENDED".equals(room.status()) ? -room.roomId() : room.roomId());
    }

    private int statusRank(String status) {
        return switch (RoomStatus.valueOf(status)) {
            case ACTIVE -> 0;
            case SELECTING -> 1;
            case PENDING -> 2;
            default -> 3;
        };
    }
}
