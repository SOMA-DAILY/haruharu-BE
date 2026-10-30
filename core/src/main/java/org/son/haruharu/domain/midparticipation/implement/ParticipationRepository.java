package org.son.haruharu.domain.midparticipation.implement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ParticipationRepository {
    ParticipationPreference preference(Long userId);
    void savePreference(ParticipationPreference preference);
    ParticipationCampaign saveCampaign(ParticipationCampaign campaign);
    Optional<ParticipationCampaign> campaign(Long id);
    Optional<ParticipationCampaign> campaignForUpdate(Long id);
    List<ParticipationCampaign> campaigns(Long roomId, int page, int limit);
    ParticipationInvitation saveInvitation(ParticipationInvitation invitation);
    Optional<ParticipationInvitation> invitation(Long id);
    Optional<ParticipationInvitation> invitationForUpdate(Long id);
    List<ParticipationInvitation> invitations(Long userId, Long before, int limit);
    List<ParticipationInvitation> campaignInvitations(Long campaignId);
    List<ParticipationInvitation> pendingNotifications(LocalDateTime staleBefore,int limit);
    boolean claimNotification(Long invitationId,int version,LocalDateTime now,LocalDateTime staleBefore);
    void finishNotification(Long invitationId,int version);
}
