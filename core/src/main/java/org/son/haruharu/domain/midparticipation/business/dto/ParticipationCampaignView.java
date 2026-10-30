package org.son.haruharu.domain.midparticipation.business.dto;
import java.time.LocalDateTime;
import java.util.List;
public record ParticipationCampaignView(Long campaignId,Long roomId,int seats,int acceptedCount,String status,
        LocalDateTime expiresAt,List<Recipient> recipients) {
    public record Recipient(Long invitationId,Long userId,String status,int deliveryVersion,LocalDateTime deliveredAt,LocalDateTime acceptedAt,LocalDateTime declinedAt) {}
}
