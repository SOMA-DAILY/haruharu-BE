package org.son.haruharu.domain.chat.business.dto;

import java.time.LocalDateTime;

public record OperatorMissionRecordingResponse(boolean available, String reason, Long roomId,
        LocalDateTime slotStart, LocalDateTime nextAvailableAt, LocalDateTime recordingClosesAt, LocalDateTime serverNow) { }
