package org.son.haruharu.domain.room.implement;

import java.time.LocalDateTime;

/** Day3 최종 호감 선택(멤버당 1행). 선택·상호매칭 판정 로직은 #41. */
public record FinalSelection(
        Long id,
        Long roomMemberId,
        Long selectedMemberId,
        LocalDateTime submittedAt
) {
    public static FinalSelection of(Long roomMemberId, Long selectedMemberId, LocalDateTime submittedAt) {
        return new FinalSelection(null, roomMemberId, selectedMemberId, submittedAt);
    }
}
