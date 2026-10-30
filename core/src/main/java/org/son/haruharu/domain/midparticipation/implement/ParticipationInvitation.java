package org.son.haruharu.domain.midparticipation.implement;
import java.time.LocalDateTime;
import org.son.haruharu.domain.midparticipation.implement.exception.ParticipationUnavailableException;

public record ParticipationInvitation(Long id, Long userId, Long campaignId, Kind kind,
        int deliveryVersion, int seenVersion, LocalDateTime declinedAt, LocalDateTime acceptedAt,
        Long acceptedMemberId, LocalDateTime createdAt, LocalDateTime deliveredAt) {
    public enum Kind { INTEREST, OFFER }
    public static ParticipationInvitation interest(Long userId, LocalDateTime now) {
        return new ParticipationInvitation(null,userId,null,Kind.INTEREST,1,0,null,null,null,now,now);
    }
    public static ParticipationInvitation offer(Long userId, Long campaignId, LocalDateTime now) {
        return new ParticipationInvitation(null,userId,campaignId,Kind.OFFER,1,0,null,null,null,now,now);
    }
    public boolean popupPending() { return seenVersion < deliveryVersion; }
    public ParticipationInvitation seen(int version) {
        int seen = Math.max(seenVersion, Math.min(version, deliveryVersion));
        return new ParticipationInvitation(id,userId,campaignId,kind,deliveryVersion,seen,declinedAt,acceptedAt,acceptedMemberId,createdAt,deliveredAt);
    }
    public ParticipationInvitation resend(LocalDateTime now) {
        if (declinedAt != null || acceptedAt != null) throw new ParticipationUnavailableException();
        return new ParticipationInvitation(id,userId,campaignId,kind,deliveryVersion+1,seenVersion,null,null,null,createdAt,now);
    }
    public ParticipationInvitation decline(LocalDateTime now) {
        if (acceptedAt != null) throw new ParticipationUnavailableException();
        return new ParticipationInvitation(id,userId,campaignId,kind,deliveryVersion,deliveryVersion,now,null,null,createdAt,deliveredAt);
    }
    public ParticipationInvitation accept(Long memberId, LocalDateTime now) {
        if (declinedAt != null) throw new ParticipationUnavailableException();
        if (acceptedAt != null) {
            if (java.util.Objects.equals(acceptedMemberId,memberId)) return this;
            throw new ParticipationUnavailableException();
        }
        return new ParticipationInvitation(id,userId,campaignId,kind,deliveryVersion,deliveryVersion,null,now,memberId,createdAt,deliveredAt);
    }
}
