package org.son.haruharu.devsupport.dto;

import jakarta.validation.constraints.NotEmpty;

import java.time.LocalTime;
import java.util.List;

/** day·time 생략 시 현재 시각 기준 Day1로 시작한다. time을 주면 가상 시계도 함께 고정된다. */
public record DevRoomCreateRequest(
        @NotEmpty List<Long> userIds,
        Integer day,
        LocalTime time
) {}
