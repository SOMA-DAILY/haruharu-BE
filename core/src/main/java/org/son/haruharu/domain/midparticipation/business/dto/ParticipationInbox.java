package org.son.haruharu.domain.midparticipation.business.dto;
import java.time.LocalDateTime;
import java.util.List;
public record ParticipationInbox(boolean enabled,List<ParticipationInvitationView> invitations,Long nextCursor,LocalDateTime serverNow) {}
