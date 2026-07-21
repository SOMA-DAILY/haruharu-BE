package org.son.haruharu.domain.room.business.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 데모 방 생성·Day 진행 응답(#265). */
public record DemoRoomResponse(
        @JsonProperty("room_id") Long roomId,
        int day
) {
}
