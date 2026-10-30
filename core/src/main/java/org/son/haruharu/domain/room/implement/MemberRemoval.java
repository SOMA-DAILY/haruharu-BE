package org.son.haruharu.domain.room.implement;
import java.time.LocalDateTime;
public record MemberRemoval(Long memberId, Long roomId, Long userId, LocalDateTime removedAt, String reason, Long adminId) {}
