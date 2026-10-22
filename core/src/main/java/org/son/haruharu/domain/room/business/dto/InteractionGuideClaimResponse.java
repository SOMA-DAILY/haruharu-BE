package org.son.haruharu.domain.room.business.dto;

import java.time.LocalDateTime;
import org.son.haruharu.domain.room.implement.InteractionGuideClaim;
import org.son.haruharu.domain.room.implement.InteractionGuideKind;

public record InteractionGuideClaimResponse(boolean claimed, InteractionGuideKind kind,
        String claimId, LocalDateTime claimedAt, String reason) {
    public static InteractionGuideClaimResponse accepted(InteractionGuideClaim claim) {
        return new InteractionGuideClaimResponse(true, claim.kind(), claim.id(), claim.claimedAt(), null);
    }
    public static InteractionGuideClaimResponse denied(InteractionGuideKind kind, String reason) {
        return new InteractionGuideClaimResponse(false, kind, null, null, reason);
    }
}
