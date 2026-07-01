package org.son.haruharu.domain.payment.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IapWebhookEventJpaRepository extends JpaRepository<IapWebhookEventEntity, UUID> {

    boolean existsByEventId(String eventId);
}
