package org.son.haruharu.domain.midparticipation.storage;
import java.util.*;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.PageRequest;
import org.son.haruharu.domain.midparticipation.implement.*;
@Repository @RequiredArgsConstructor
public class ParticipationRepositoryImpl implements ParticipationRepository {
    private final jakarta.persistence.EntityManager entityManager;
    private final ParticipationPreferenceJpaRepository preferences;
    private final ParticipationCampaignJpaRepository campaigns;
    private final ParticipationInvitationJpaRepository invitations;
    public ParticipationPreference preference(Long id) { return preferences.findById(id).map(ParticipationPreferenceEntity::toDomain).orElse(new ParticipationPreference(id,false)); }
    public void savePreference(ParticipationPreference p) { preferences.save(ParticipationPreferenceEntity.from(p)); }
    public ParticipationCampaign saveCampaign(ParticipationCampaign c) { return campaigns.saveAndFlush(ParticipationCampaignEntity.from(c)).toDomain(); }
    public Optional<ParticipationCampaign> campaign(Long id) { return campaigns.findById(id).map(ParticipationCampaignEntity::toDomain); }
    public Optional<ParticipationCampaign> campaignForUpdate(Long id) { return campaigns.locked(id).map(e -> { entityManager.refresh(e,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE); return e.toDomain(); }); }
    public List<ParticipationCampaign> campaigns(Long roomId,int offset,int limit) { return campaigns.findByRoomIdOrderByIdDesc(roomId,PageRequest.of(offset/limit,limit)).stream().map(ParticipationCampaignEntity::toDomain).toList(); }
    public ParticipationInvitation saveInvitation(ParticipationInvitation i) { return invitations.saveAndFlush(ParticipationInvitationEntity.from(i)).toDomain(); }
    public Optional<ParticipationInvitation> invitation(Long id) { return invitations.findById(id).map(ParticipationInvitationEntity::toDomain); }
    public Optional<ParticipationInvitation> invitationForUpdate(Long id) { return invitations.locked(id).map(e -> { entityManager.refresh(e,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE); return e.toDomain(); }); }
    public List<ParticipationInvitation> invitations(Long userId,Long before,int limit) { return invitations.forUser(userId,before,PageRequest.of(0,limit)).stream().map(ParticipationInvitationEntity::toDomain).toList(); }
    public List<ParticipationInvitation> campaignInvitations(Long id) { return invitations.findByCampaignIdOrderByUserIdAsc(id).stream().map(ParticipationInvitationEntity::toDomain).toList(); }
    public List<ParticipationInvitation> pendingNotifications(LocalDateTime staleBefore,int limit) { return invitations.pendingNotifications(staleBefore,PageRequest.of(0,limit)).stream().map(ParticipationInvitationEntity::toDomain).toList(); }
    public boolean claimNotification(Long id,int version,LocalDateTime now,LocalDateTime staleBefore) { return invitations.claimNotification(id,version,now,staleBefore)==1; }
    public void finishNotification(Long id,int version) { invitations.finishNotification(id,version); }
    public void withdrawInvitations(Long userId,LocalDateTime now) { invitations.withdraw(userId,now,ParticipationInvitation.Kind.OFFER); }
}
