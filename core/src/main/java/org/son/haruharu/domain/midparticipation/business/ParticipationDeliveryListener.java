package org.son.haruharu.domain.midparticipation.business;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import org.son.haruharu.domain.midparticipation.business.event.ParticipationDeliveryEvent;
import org.son.haruharu.domain.notification.business.PushSendService;
import org.son.haruharu.domain.notification.implement.NotificationType;
@Component @RequiredArgsConstructor @Slf4j
public class ParticipationDeliveryListener {
    private final MidParticipationService service;
    private final PushSendService push;
    private final org.son.haruharu.domain.notification.business.NotificationHistoryService history;
    private final ParticipationNotificationClaimService claims;
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void deliver(ParticipationDeliveryEvent event) {
        if(!claims.claim(event.invitationId(),event.deliveryVersion())) return;
        try {
            var invitation=service.invitation(event.userId(),event.invitationId());
            if(invitation.deliveryVersion()!=event.deliveryVersion()) { claims.finish(event.invitationId(),event.deliveryVersion());return; }
            if(!Set.of("INTEREST","AVAILABLE").contains(invitation.status())) { claims.finish(event.invitationId(),event.deliveryVersion());return; }
            var payload=new HashMap<String,String>();
            payload.put("invitation_id",event.invitationId().toString());
            if(invitation.roomId()!=null) payload.put("room_id",invitation.roomId().toString());
            var type="INTEREST".equals(invitation.kind()) ? NotificationType.MID_PARTICIPATION_INTEREST : NotificationType.MID_PARTICIPATION_OFFER;
            String title=type.title(),body=type.body();
            if("OFFER".equals(invitation.kind())) {
                String cohort=invitation.cohortSequence()==null?"하루방":invitation.cohortSequence()+"기";
                title=(invitation.cohortSequence()==null?"":cohort+" ")+"중간 참여 초대";
                body="현재 "+(invitation.currentDay()==null?"":invitation.currentDay()+"일차가 ")+"진행 중인 "+cohort+"에 초대합니다. 참여하시겠어요?";
            }
            // 알림함 저장 실패는 삼키지 않는다. 다음 worker 실행에서 다시 시도한다.
            history.saveAll(List.of(event.userId()),type,title,body,payload);
            push.push(List.of(event.userId()),type,payload,title,body,org.son.haruharu.infrastructure.fcm.PushStyle.NORMAL);
            claims.finish(event.invitationId(),event.deliveryVersion());
        } catch(Exception e) { log.warn("중간 참여 안내 발송 실패: {}",e.getClass().getSimpleName()); }
    }
}
