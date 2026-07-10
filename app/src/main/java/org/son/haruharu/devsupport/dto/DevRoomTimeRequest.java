package org.son.haruharu.devsupport.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record DevRoomTimeRequest(
        @NotNull Integer day,
        @NotNull LocalTime time
) {}
