package org.son.haruharu.admin.domain.room.implement;

import org.springframework.stereotype.Component;
import org.son.haruharu.admin.domain.room.implement.exception.InvalidRoomActivityFilterException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component
public class AdminRoomActivityReader {
    private final RoomActivityRepository repository;
    private final AdminRoomReader rooms;

    public AdminRoomActivityReader(RoomActivityRepository repository, AdminRoomReader rooms) {
        this.repository = repository;
        this.rooms = rooms;
    }

    public List<RoomActivity> find(RoomActivity.Filter filter, int offset, int limit, boolean oldestFirst) {
        return repository.find(filter, offset, limit, oldestFirst);
    }

    public long count(RoomActivity.Filter filter) { return repository.count(filter); }
    public List<RoomActivity.Count> counts(RoomActivity.Filter filter) { return repository.counts(filter); }

    public void validatePage(int page, int size, String order) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE
                || !Set.of("asc", "desc").contains(order)) throw new InvalidRoomActivityFilterException();
    }

    public RoomActivity.Filter filter(Room room, Integer day, Long userId, String type) {
        if ((day != null && (day < 1 || day > room.totalDays())) || (userId != null && userId <= 0)
                || (type != null && !Set.of("VIDEO", "CHAT", "REACTION", "QUESTION").contains(type)))
            throw new InvalidRoomActivityFilterException();
        LocalDateTime from = day == null ? null : start(room).plusDays(day - 1L);
        return new RoomActivity.Filter(room.id(), from, from == null ? null : from.plusDays(1), userId, type);
    }

    private LocalDateTime start(Room room) {
        if (room.startsAt() != null) return room.startsAt();
        if (room.startedAt() != null) return room.dateOfDay(1).atTime(Room.DAY_START_HOUR, 0);
        return room.scheduledDate().atTime(Room.DAY_START_HOUR, 0);
    }

    public Integer dayOf(Room room, LocalDateTime time) {
        LocalDateTime start = start(room);
        if (time.isBefore(start) || !time.isBefore(start.plusDays(room.totalDays()))) return null;
        return (int) ChronoUnit.DAYS.between(start, time) + 1;
    }

    public Map<Long, RoomActivity.Participant> participants(Long roomId) {
        List<RoomMember> members = rooms.membersOf(roomId);
        return members.stream().collect(Collectors.toMap(RoomMember::userId,
                m -> new RoomActivity.Participant(m.userId(), m.pseudonym() != null && !m.pseudonym().isBlank() ? m.pseudonym() : "가명 없음"), (a, b) -> a));
    }

    public RoomActivity.Participant participant(Map<Long, RoomActivity.Participant> members, Long userId) {
        return userId == null ? null : members.getOrDefault(userId, new RoomActivity.Participant(userId, "참여자 #" + userId));
    }

    public RoomActivity.Statistics statistics(List<RoomActivity.Count> counts, Long userId) {
        Predicate<RoomActivity.Count> actor = c -> userId == null || Objects.equals(c.actorUserId(), userId);
        Predicate<RoomActivity.Count> target = c -> userId == null || Objects.equals(c.targetUserId(), userId);
        return new RoomActivity.Statistics(sum(counts, "VIDEO", actor), sum(counts, "CHAT", actor),
                sum(counts, "REACTION", actor), sum(counts, "QUESTION", target),
                sum(counts, "REACTION", target), sum(counts, "QUESTION", target));
    }

    public List<RoomActivity.MemberStatistics> memberStatistics(Long roomId, Long userId, List<RoomActivity.Count> counts) {
        return participants(roomId).values().stream().sorted(java.util.Comparator.comparingLong(RoomActivity.Participant::userId))
                .filter(m -> userId == null || userId.equals(m.userId()))
                .map(m -> new RoomActivity.MemberStatistics(m, statistics(counts, m.userId()))).toList();
    }

    private long sum(List<RoomActivity.Count> counts, String type, Predicate<RoomActivity.Count> predicate) {
        return counts.stream().filter(c -> c.type().equals(type)).filter(predicate).mapToLong(RoomActivity.Count::count).sum();
    }
}
