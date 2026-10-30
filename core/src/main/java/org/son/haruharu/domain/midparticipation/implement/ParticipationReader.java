package org.son.haruharu.domain.midparticipation.implement;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.son.haruharu.domain.midparticipation.implement.exception.ParticipationNotFoundException;

@Component @RequiredArgsConstructor
public class ParticipationReader {
    private final ParticipationRepository repository;
    public List<ParticipationInvitation> pendingNotifications(java.time.LocalDateTime staleBefore,int limit) { return repository.pendingNotifications(staleBefore,limit); }
    public ParticipationPreference preference(Long userId) { return repository.preference(userId); }
    public ParticipationCampaign campaign(Long id) { return repository.campaign(id).orElseThrow(ParticipationNotFoundException::new); }
    public ParticipationCampaign lockedCampaign(Long id) { return repository.campaignForUpdate(id).orElseThrow(ParticipationNotFoundException::new); }
    public ParticipationInvitation invitation(Long id) { return repository.invitation(id).orElseThrow(ParticipationNotFoundException::new); }
    public ParticipationInvitation lockedInvitation(Long id) { return repository.invitationForUpdate(id).orElseThrow(ParticipationNotFoundException::new); }
    public List<ParticipationInvitation> invitations(Long userId, Long before, int limit) { return repository.invitations(userId,before,limit); }
    public List<ParticipationInvitation> recipients(Long campaignId) { return repository.campaignInvitations(campaignId); }
    public List<ParticipationCampaign> campaigns(Long roomId,int offset,int limit) { return repository.campaigns(roomId,offset,limit); }
}
