package org.son.haruharu.domain.payment.storage;

import org.son.haruharu.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.domain.payment.implement.IapWebhookEvent;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "iap_webhook_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IapWebhookEventEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String eventId;

    @Column(nullable = false, length = 50)
    private String eventType;

    @Column(columnDefinition = "uuid")
    private UUID purchaseId;

    @Column(columnDefinition = "text")
    private String payload;

    @Column(nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    public static IapWebhookEventEntity from(IapWebhookEvent event) {
        var entity = new IapWebhookEventEntity();
        entity.id = event.id();
        entity.eventId = event.eventId();
        entity.eventType = event.eventType();
        entity.purchaseId = event.purchaseId();
        entity.payload = event.payload();
        entity.receivedAt = event.receivedAt();
        return entity;
    }

    public IapWebhookEvent toDomain() {
        return new IapWebhookEvent(id, eventId, eventType, purchaseId, payload, receivedAt);
    }
}
