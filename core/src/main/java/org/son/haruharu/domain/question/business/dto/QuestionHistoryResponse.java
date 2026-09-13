package org.son.haruharu.domain.question.business.dto;

import org.son.haruharu.domain.room.implement.Room;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record QuestionHistoryResponse(List<HistoryRoom> rooms, Long nextCursor) {
    public record HistoryRoom(Long roomId, String status, LocalDateTime startedAt) {}

    public static QuestionHistoryResponse from(
            List<Room> rooms, Long nextCursor, LocalDateTime now) {
        return new QuestionHistoryResponse(
                rooms.stream()
                        .sorted(Comparator.comparing(Room::id).reversed())
                        .map(
                                room ->
                                        new HistoryRoom(
                                                room.id(),
                                                room.statusAt(now).name(),
                                                room.startsAt() == null
                                                        ? room.startedAt()
                                                        : room.startsAt()))
                        .toList(),
                nextCursor);
    }
}
