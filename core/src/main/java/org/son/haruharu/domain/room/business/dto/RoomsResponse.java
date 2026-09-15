package org.son.haruharu.domain.room.business.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

public record RoomsResponse(
        LocalDateTime now,
        List<RoomSummary> rooms,
        @JsonProperty("next_ended_cursor") Long nextEndedCursor
) {
    public RoomsResponse(LocalDateTime now, List<RoomSummary> rooms) {
        this(now, rooms, null);
    }

    public record RoomSummary(
            @JsonProperty("room_id") Long roomId,
            String status,
            @JsonProperty("current_day") int currentDay,
            @JsonProperty("total_days") int totalDays,
            @JsonProperty("profile_image_urls")
            @ArraySchema(schema = @Schema(types = {"string", "null"}))
            List<String> profileImageUrls,
            @JsonProperty("timer_type") String timerType,
            @JsonProperty("timer_target_at") LocalDateTime timerTargetAt,
            @JsonProperty("refresh_at") LocalDateTime refreshAt
    ) {
    }
}
