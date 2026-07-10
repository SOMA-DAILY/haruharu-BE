package org.son.haruharu.devsupport.dto;

import java.time.LocalDate;

public record DevMatchingRunResponse(
        LocalDate scheduledDate,
        int roomsCreated,
        int roomsActivated
) {}
