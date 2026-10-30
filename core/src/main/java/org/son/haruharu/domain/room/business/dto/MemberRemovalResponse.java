package org.son.haruharu.domain.room.business.dto;
import java.time.LocalDateTime;
import org.son.haruharu.domain.room.implement.MemberRemoval;
public record MemberRemovalResponse(Long memberId,Long roomId,Long userId,LocalDateTime removedAt) {
    public static MemberRemovalResponse from(MemberRemoval r) { return new MemberRemovalResponse(r.memberId(),r.roomId(),r.userId(),r.removedAt()); }
}
