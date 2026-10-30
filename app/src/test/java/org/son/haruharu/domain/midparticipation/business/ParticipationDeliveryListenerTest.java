package org.son.haruharu.domain.midparticipation.business;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.midparticipation.business.dto.ParticipationInvitationView;
import org.son.haruharu.domain.midparticipation.business.event.ParticipationDeliveryEvent;
import org.son.haruharu.domain.notification.business.NotificationHistoryService;
import org.son.haruharu.domain.notification.business.PushSendService;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.infrastructure.fcm.PushStyle;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ParticipationDeliveryListenerTest {
    @Mock MidParticipationService service;
    @Mock PushSendService push;
    @Mock NotificationHistoryService history;
    @Mock ParticipationNotificationClaimService claims;
    ParticipationDeliveryListener listener;
    private final ParticipationDeliveryEvent event = new ParticipationDeliveryEvent(1L, 2L, 1);

    @BeforeEach void setUp() {
        listener = new ParticipationDeliveryListener(service, push, history, claims);
    }

    private void available() {
        given(claims.claim(1L, 1)).willReturn(true);
        given(service.invitation(2L, 1L)).willReturn(new ParticipationInvitationView(
                1L, "INTEREST", "INTEREST", 1, true, null, null, null, null, null,
                null, null, null, null, false));
    }

    @Test void inboxFailureLeavesClaimUnfinishedForRetry() {
        available();
        willThrow(new IllegalStateException("temporary database failure"))
                .given(history).saveAll(any(), any(), any(), any(), any());
        listener.deliver(event);
        then(claims).should(never()).finish(anyLong(), anyInt());
        then(push).shouldHaveNoInteractions();
    }

    @Test void persistedNoticeIsDispatchedAndMarkedDelivered() {
        available();
        listener.deliver(event);
        var type = NotificationType.MID_PARTICIPATION_INTEREST;
        var payload = Map.of("invitation_id", "1");
        then(history).should().saveAll(List.of(2L), type, type.title(), type.body(), payload);
        then(push).should().push(List.of(2L), type, payload, type.title(), type.body(), PushStyle.NORMAL);
        then(claims).should().finish(1L, 1);
    }
    @Test void offerPushAndInboxUseTheRealCohortNumberAndCurrentDay() {
        given(claims.claim(1L,1)).willReturn(true);
        given(service.invitation(2L,1L)).willReturn(new ParticipationInvitationView(
                1L,"OFFER","AVAILABLE",1,true,5L,42L,99L,8,2,null,null,null,1,false));
        listener.deliver(event);
        var type=NotificationType.MID_PARTICIPATION_OFFER;
        var payload=Map.of("invitation_id","1","room_id","42");
        String title="8기 중간 참여 초대",body="현재 2일차가 진행 중인 8기에 초대합니다. 참여하시겠어요?";
        then(history).should().saveAll(List.of(2L),type,title,body,payload);
        then(push).should().push(List.of(2L),type,payload,title,body,PushStyle.NORMAL);
        then(claims).should().finish(1L,1);
    }

}
