package org.son.haruharu.domain.payment.storage;

import org.springframework.data.jpa.repository.JpaRepository;


public interface IapWebhookEventJpaRepository extends JpaRepository<IapWebhookEventEntity, Long> {

    boolean existsByEventId(String eventId);
}
