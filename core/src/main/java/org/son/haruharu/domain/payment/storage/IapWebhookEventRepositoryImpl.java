package org.son.haruharu.domain.payment.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.payment.implement.IapWebhookEvent;
import org.son.haruharu.domain.payment.implement.IapWebhookEventRepository;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class IapWebhookEventRepositoryImpl implements IapWebhookEventRepository {

    private final IapWebhookEventJpaRepository jpaRepository;

    @Override
    public IapWebhookEvent save(IapWebhookEvent event) {
        return jpaRepository.save(IapWebhookEventEntity.from(event)).toDomain();
    }

    @Override
    public boolean existsByEventId(String eventId) {
        return jpaRepository.existsByEventId(eventId);
    }
}
