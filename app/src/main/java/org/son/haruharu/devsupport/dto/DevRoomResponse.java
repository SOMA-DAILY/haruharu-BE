package org.son.haruharu.devsupport.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DevRoomResponse(
        Long roomId,
        LocalDate scheduledDate,
        LocalDateTime startedAt,
        int currentDay,
        DevClockResponse clock
) {}
