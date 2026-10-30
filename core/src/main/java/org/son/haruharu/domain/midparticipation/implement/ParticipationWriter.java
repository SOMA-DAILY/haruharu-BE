package org.son.haruharu.domain.midparticipation.implement;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class ParticipationWriter {
    private final ParticipationRepository repository;
    public ParticipationCampaign save(ParticipationCampaign campaign) { return repository.saveCampaign(campaign); }
    public ParticipationInvitation save(ParticipationInvitation invitation) { return repository.saveInvitation(invitation); }
    public void preference(Long userId,boolean enabled) { repository.savePreference(new ParticipationPreference(userId,enabled)); }
    public boolean claimNotification(Long id,int version,LocalDateTime now,LocalDateTime staleBefore) { return repository.claimNotification(id,version,now,staleBefore); }
    public void finishNotification(Long id,int version) { repository.finishNotification(id,version); }
}
