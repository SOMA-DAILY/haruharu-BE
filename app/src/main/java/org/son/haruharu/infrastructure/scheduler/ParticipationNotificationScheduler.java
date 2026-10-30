package org.son.haruharu.infrastructure.scheduler;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.son.haruharu.domain.midparticipation.implement.ParticipationReader;
import org.son.haruharu.domain.midparticipation.business.ParticipationDeliveryListener;
import org.son.haruharu.domain.midparticipation.business.event.ParticipationDeliveryEvent;
@Component @RequiredArgsConstructor
@ConditionalOnProperty(name="haruharu.mid-participation.delivery-enabled",havingValue="true",matchIfMissing=true)
public class ParticipationNotificationScheduler {
    private final ParticipationReader reader;
    private final ParticipationDeliveryListener deliveries;
    private final Clock clock;
    @Scheduled(fixedDelayString="${haruharu.mid-participation.delivery-delay-ms:2000}",initialDelay=2000)
    public void deliverPending() {
        for(var i:reader.pendingNotifications(LocalDateTime.now(clock).minusMinutes(1),50))
            deliveries.deliver(new ParticipationDeliveryEvent(i.id(),i.userId(),i.deliveryVersion()));
    }
}
