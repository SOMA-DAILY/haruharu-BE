package org.son.haruharu.domain.midparticipation.business;
import static org.son.haruharu.domain.midparticipation.implement.ParticipationPolicy.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.son.haruharu.domain.midparticipation.implement.*;
import org.son.haruharu.domain.midparticipation.implement.exception.*;
import org.son.haruharu.domain.midparticipation.business.dto.*;
import org.son.haruharu.domain.midparticipation.business.event.ParticipationDeliveryEvent;
import org.son.haruharu.domain.room.implement.*;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.user.implement.UserReader;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ParticipationAdminService {
    private final ParticipationReader reader;
    private final ParticipationWriter writer;
    private final RoomReader rooms;
    private final RoomMemberReader members;
    private final RoomParticipationGate gate;
    private final UserReader users;
    private final ApplicationEventPublisher events;
    private final org.son.haruharu.domain.cohort.implement.CohortReader cohorts;
    private final Clock clock;
    public record Availability(Long roomId,int capacity,long memberCount,int available,boolean recruiting,LocalDateTime finalSelectionOpensAt,LocalDateTime defaultExpiresAt,Integer cohortSequence) {}
    public Availability availability(Long roomId) {
        var room=rooms.findById(roomId).orElseThrow(RoomNotFoundException::new);
        int available=gate.available(room);long count=members.countByRoomId(roomId);
        var now=LocalDateTime.now(clock);
        boolean recruiting=room.statusAt(now)==RoomStatus.ACTIVE && room.finalSelectionOpensAt()!=null && now.isBefore(room.finalSelectionOpensAt());
        var cohortSequence=room.composition().cohortId()==null?null:cohorts.findById(room.composition().cohortId()).map(org.son.haruharu.domain.cohort.implement.Cohort::sequence).orElse(null);
        return new Availability(roomId,(int)count+available,count,available,recruiting,room.finalSelectionOpensAt(),
                recruiting?expiry(room,now,null):null,cohortSequence);
    }
    @Transactional
    public List<Long> outreach(List<Long> userIds) {
        var now=LocalDateTime.now(clock);
        var result=new ArrayList<Long>();
        for(Long userId:ids(userIds)) {
            users.lockById(userId);
            requireActive(userId);
            if(reader.preference(userId).enabled()) continue;
            var invitation=writer.save(ParticipationInvitation.interest(userId,now));
            deliver(invitation);
            result.add(invitation.id());
        }
        return result;
    }
    @Transactional
    public ParticipationCampaignView createCampaign(Long roomId,int seats,LocalDateTime requestedExpiry,List<Long> userIds) {
        var room=rooms.findByIdForUpdate(roomId).orElseThrow(RoomNotFoundException::new);
        var now=LocalDateTime.now(clock);
        requireRecruiting(room,now);
        gate.initialize(room);
        if(seats<1 || seats>gate.available(room)) throw new ParticipationUnavailableException("충원 인원은 현재 빈자리 안에서 지정해 주세요.");
        var expires=expiry(room,now,requestedExpiry);
        var campaign=writer.save(new ParticipationCampaign(null,roomId,seats,0,expires,null,now));
        int sent=0;
        for(Long userId:ids(userIds)) {
            users.lockById(userId);
            if(!eligible(roomId,userId)) continue;
            var invitation=writer.save(ParticipationInvitation.offer(userId,campaign.id(),now));
            deliver(invitation);sent++;
        }
        if(sent==0) throw new ParticipationUnavailableException("제안을 보낼 수 있는 참가자가 없어요. 이미 참여 중이거나 퇴장한 사람은 제외됩니다.");
        return campaignView(campaign,now);
    }
    @Transactional
    public ParticipationCampaignView resend(Long campaignId,List<Long> selectedUserIds,LocalDateTime requestedExpiry) {
        var discovered=reader.campaign(campaignId);
        var room=rooms.findByIdForUpdate(discovered.roomId()).orElseThrow(RoomNotFoundException::new);
        var campaign=reader.lockedCampaign(campaignId);
        var now=LocalDateTime.now(clock);
        requireRecruiting(room,now);
        if(campaign.cancelledAt()!=null || campaign.acceptedCount()>=campaign.seats() || gate.available(room)<1)
            throw new ParticipationUnavailableException();
        var renewed=new ParticipationCampaign(campaign.id(),campaign.roomId(),campaign.seats(),campaign.acceptedCount(),
                expiry(room,now,requestedExpiry),null,campaign.createdAt());
        Set<Long> selected=selectedUserIds==null?Set.of():new HashSet<>(selectedUserIds);
        int sent=0;
        for(var recipient:reader.recipients(campaignId)) {
            if(!selected.isEmpty() && !selected.contains(recipient.userId())) continue;
            if(recipient.declinedAt()!=null || recipient.acceptedAt()!=null) continue;
            users.lockById(recipient.userId());
            var locked=reader.lockedInvitation(recipient.id());
            if(locked.declinedAt()!=null || locked.acceptedAt()!=null || !eligible(room.id(),recipient.userId())) continue;
            var updated=writer.save(locked.resend(now));deliver(updated);sent++;
        }
        if(sent==0) throw new ParticipationUnavailableException("다시 제안할 미응답자가 없어요.");
        writer.save(renewed);
        return campaignView(renewed,now);
    }
    @Transactional
    public ParticipationCampaignView cancel(Long campaignId) {
        var discovered=reader.campaign(campaignId);
        rooms.findByIdForUpdate(discovered.roomId()).orElseThrow(RoomNotFoundException::new);
        var now=LocalDateTime.now(clock);
        var campaign=writer.save(reader.lockedCampaign(campaignId).cancel(now));
        return campaignView(campaign,now);
    }
    public List<ParticipationCampaignView> campaigns(Long roomId,int page,int size) {
        int limit=Math.max(1,Math.min(size,100));
        var now=LocalDateTime.now(clock);
        return reader.campaigns(roomId,Math.max(0,page)*limit,limit).stream().map(c->campaignView(c,now)).toList();
    }
    private boolean eligible(Long roomId,Long userId) {
        return users.findActiveIds(List.of(userId)).contains(userId)
                && members.findByRoomIdAndUserId(roomId,userId).isEmpty() && !isRemoved(roomId,userId);
    }
    private boolean isRemoved(Long roomId,Long userId) {
        try { gate.assertNotRemoved(roomId,userId);return false; } catch(ParticipationUnavailableException e) { return true; }
    }
    private void requireActive(Long userId) {
        if(!users.findActiveIds(List.of(userId)).contains(userId)) throw new ParticipationUnavailableException();
    }
    private void deliver(ParticipationInvitation invitation) {
        events.publishEvent(new ParticipationDeliveryEvent(invitation.id(),invitation.userId(),invitation.deliveryVersion()));
    }
    private ParticipationCampaignView campaignView(ParticipationCampaign c,LocalDateTime now) {
        var room=rooms.findById(c.roomId()).orElse(null);
        String status=c.cancelledAt()!=null?"CANCELLED":c.acceptedCount()>=c.seats()?"FULL":
                !now.isBefore(c.expiresAt())?"EXPIRED":room==null || room.statusAt(now)!=RoomStatus.ACTIVE?"EXPIRED":
                gate.available(room)<1?"FULL":"OPEN";
        var recipients=reader.recipients(c.id()).stream().map(i->new ParticipationCampaignView.Recipient(
                i.id(),i.userId(),i.acceptedAt()!=null?"ACCEPTED":i.declinedAt()!=null?"DECLINED":"PENDING",
                i.deliveryVersion(),i.deliveredAt(),i.acceptedAt(),i.declinedAt())).toList();
        return new ParticipationCampaignView(c.id(),c.roomId(),c.seats(),c.acceptedCount(),status,c.expiresAt(),recipients);
    }
}
