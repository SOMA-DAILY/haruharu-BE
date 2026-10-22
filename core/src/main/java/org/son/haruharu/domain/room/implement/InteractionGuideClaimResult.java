package org.son.haruharu.domain.room.implement;

public record InteractionGuideClaimResult(InteractionGuideKind kind, InteractionGuideClaim claim, String reason) {
    public static InteractionGuideClaimResult accepted(InteractionGuideClaim claim) {
        return new InteractionGuideClaimResult(claim.kind(), claim, null);
    }
    public static InteractionGuideClaimResult denied(InteractionGuideKind kind, String reason) {
        return new InteractionGuideClaimResult(kind, null, reason);
    }
}
