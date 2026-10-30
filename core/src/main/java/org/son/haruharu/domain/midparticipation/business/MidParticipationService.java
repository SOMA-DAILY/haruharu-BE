package org.son.haruharu.domain.midparticipation.business;

import static org.son.haruharu.domain.midparticipation.implement.ParticipationPolicy.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.son.haruharu.domain.midparticipation.implement.*;
import org.son.haruharu.domain.midparticipation.implement.exception.*;
import org.son.haruharu.domain.midparticipation.business.dto.*;
import org.son.haruharu.domain.room.implement.*;
import org.son.haruharu.domain.room.business.RoomMemberJoiner;
import org.son.haruharu.domain.room.business.dto.JoinRoomResponse;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.user.implement.UserReader;

/** 잠금 순서: room → campaign → user(오름차순) → invitation. 알림은 커밋 이후 보낸다. */
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class MidParticipationService {
    private final ParticipationReader reader;
    private final ParticipationWriter writer;
    private final RoomReader rooms;
    private final RoomMemberReader members;
    private final RoomParticipationGate gate;
    private final RoomMemberJoiner joiner;
    private final UserReader users;
    private final org.son.haruharu.domain.room.business.RoomEntryIdentityService entryIdentity;
    private final org.son.haruharu.domain.cohort.implement.CohortReader cohorts;
    private final Clock clock;

    public ParticipationVisibilityResponse visibility(Long userId) {
        var state=gate.visibility(userId);
        return new ParticipationVisibilityResponse(state.version(),state.removedRoomIds(),state.closedChatIds());
    }
    public ParticipationInvitationView prompt(Long userId) {
        boolean enabled=reader.preference(userId).enabled();var now=LocalDateTime.now(clock);
        Long before=null;
        while(true) {
            var batch=reader.invitations(userId,before,100);
            for(var i:batch) {
                if(i.kind()!=ParticipationInvitation.Kind.OFFER || !i.popupPending() || i.acceptedAt()!=null || i.declinedAt()!=null) continue;
                var result=view(i,enabled,now);
                if(result.popupPending()) return result;
            }
            if(batch.size()<100) return null;
            before=batch.get(batch.size()-1).id();
        }
    }
    public ParticipationInbox inbox(Long userId,Long before,int size) {
        int limit=Math.max(1,Math.min(size,100));
        var rows=reader.invitations(userId,before,limit+1);
        boolean more=rows.size()>limit;
        var page=rows.stream().limit(limit).toList();
        var now=LocalDateTime.now(clock);
        boolean enabled=reader.preference(userId).enabled();
        return new ParticipationInbox(enabled,page.stream().map(i->view(i,enabled,now)).toList(),
                more?page.get(page.size()-1).id():null,now);
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,readOnly=true)
    public ParticipationInvitationView invitation(Long userId,Long invitationId) {
        var invitation=owned(reader.invitation(invitationId),userId);
        return view(invitation,reader.preference(userId).enabled(),LocalDateTime.now(clock));
    }
    private ParticipationInvitation owned(ParticipationInvitation i,Long userId) {
        if(!i.userId().equals(userId)) throw new ParticipationNotFoundException();
        return i;
    }
    @Transactional
    public ParticipationPreferenceResponse preference(Long userId,boolean enabled) {
        users.lockById(userId);
        requireActive(userId);
        writer.preference(userId,enabled);
        return new ParticipationPreferenceResponse(userId,enabled);
    }
    @Transactional
    public void seen(Long userId,Long invitationId,int version) {
        var invitation=owned(reader.lockedInvitation(invitationId),userId);
        writer.save(invitation.seen(version));
    }
    @Transactional
    public void decline(Long userId,Long invitationId) {
        users.lockById(userId);
        var invitation=owned(reader.lockedInvitation(invitationId),userId);
        writer.save(invitation.decline(LocalDateTime.now(clock)));
    }
    @Transactional
    public JoinRoomResponse accept(Long userId,Long invitationId) {
        var discovered=owned(reader.invitation(invitationId),userId);
        if(discovered.kind()!=ParticipationInvitation.Kind.OFFER) throw new ParticipationUnavailableException();
        var discoveredCampaign=reader.campaign(discovered.campaignId());
        var room=rooms.findByIdForUpdate(discoveredCampaign.roomId()).orElseThrow(RoomNotFoundException::new);
        var campaign=reader.lockedCampaign(discovered.campaignId());
        users.lockById(userId);
        requireActive(userId);
        var invitation=owned(reader.lockedInvitation(invitationId),userId);
        gate.assertNotRemoved(room.id(),userId);
        // 이미 성공한 네트워크 재시도는 인원 증가보다 먼저 멱등 반환한다.
        if(invitation.acceptedAt()!=null) {
            if(members.findByRoomIdAndUserId(room.id(),userId).isPresent()) return new JoinRoomResponse(room.id());
            throw new ParticipationUnavailableException();
        }
        var now=LocalDateTime.now(clock);
        requireRecruiting(room,now);
        if(invitation.declinedAt()!=null || !campaign.openAt(now))
            throw new ParticipationUnavailableException();
        if(members.findByRoomIdAndUserId(room.id(),userId).isPresent())
            throw new ParticipationUnavailableException("이미 참여 중인 방이에요.");
        if(gate.available(room)<1) throw new ParticipationUnavailableException("이미 다른 분이 참여해 자리가 마감되었어요.");
        var identity=entryIdentity.snapshot(room.id(),userId);
        var member=new RoomMember(null,room.id(),userId,identity.gender(),null,identity.birthday(),
                now,RoomEntryPath.MID_PARTICIPATION,null);
        var saved=joiner.join(member);
        writer.save(campaign.accept(now));
        writer.save(invitation.accept(saved.id(),now));
        return new JoinRoomResponse(room.id());
    }
    private void requireActive(Long userId) {
        if(!users.findActiveIds(List.of(userId)).contains(userId)) throw new ParticipationUnavailableException();
    }
    private ParticipationInvitationView view(ParticipationInvitation i,boolean enabled,LocalDateTime now) {
        if(i.kind()==ParticipationInvitation.Kind.INTEREST) {
            String status=enabled?"REGISTERED":i.declinedAt()!=null?"DECLINED":"INTEREST";
            return new ParticipationInvitationView(i.id(),"INTEREST",status,i.deliveryVersion(),
                    i.popupPending() && status.equals("INTEREST"),null,null,null,null,null,null,null,null,null,enabled);
        }
        var c=reader.campaign(i.campaignId());
        var room=rooms.findById(c.roomId()).orElse(null);
        String status=i.acceptedAt()!=null?"ACCEPTED":i.declinedAt()!=null?"DECLINED":
                c.cancelledAt()!=null?"CANCELLED":!now.isBefore(c.expiresAt())?"EXPIRED":
                room==null || room.statusAt(now)!=RoomStatus.ACTIVE?"EXPIRED":
                gate.isRemoved(c.roomId(),i.userId())?"UNAVAILABLE":
                members.findByRoomIdAndUserId(c.roomId(),i.userId()).isPresent()?"ALREADY_JOINED":
                c.acceptedCount()>=c.seats() || gate.available(room)<1?"FULL":"AVAILABLE";
        int remaining=room==null?0:Math.max(0,Math.min(c.seats()-c.acceptedCount(),gate.available(room)));
        return new ParticipationInvitationView(i.id(),"OFFER",status,i.deliveryVersion(),
                i.popupPending() && status.equals("AVAILABLE"),c.id(),c.roomId(),room==null?null:room.composition().cohortId(),
                room==null||room.composition().cohortId()==null?null:cohorts.findById(room.composition().cohortId()).map(org.son.haruharu.domain.cohort.implement.Cohort::sequence).orElse(null),room==null?null:room.currentDayAt(now),
                room==null?null:room.startsAt(),room==null?null:room.endsAt(),c.expiresAt(),remaining,enabled);
    }
}
