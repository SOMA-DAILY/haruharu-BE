package org.son.haruharu.domain.midparticipation.business.dto;
import java.time.LocalDateTime;
public record ParticipationInvitationView(Long invitationId,String kind,String status,int deliveryVersion,boolean popupPending,
        Long campaignId,Long roomId,Long cohortId,Integer cohortSequence,Integer currentDay,LocalDateTime startsAt,
        LocalDateTime endsAt,LocalDateTime expiresAt,Integer seatsRemaining,boolean enabled) {}
