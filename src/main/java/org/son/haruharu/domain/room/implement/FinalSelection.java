package org.son.haruharu.domain.room.implement;

import java.time.LocalDateTime;
import java.util.UUID;

/** Day3 최종 호감 선택(멤버당 1행). 선택·상호매칭 판정 로직은 #41. */
public record FinalSelection(
        UUID id,
        UUID roomMemberId,
        UUID selectedMemberId,
        LocalDateTime submittedAt
) {}
