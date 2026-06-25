package org.son.haruharu.domain.room.business.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record RoomResponse(
        @JsonProperty("room_id") UUID roomId,
        String status,
        @JsonProperty("scheduled_date") LocalDate scheduledDate,
        @JsonProperty("total_days") int totalDays,
        @JsonProperty("current_day") int currentDay,
        @JsonProperty("started_at") LocalDateTime startedAt,
        @JsonProperty("member_count") int memberCount
) {
    public static RoomResponse from(Room room, List<RoomMember> members) {
        return new RoomResponse(
            room.id(), room.status().name(), room.scheduledDate(),
            room.totalDays(), room.currentDay(), room.startedAt(), members.size()
        );
    }
}
