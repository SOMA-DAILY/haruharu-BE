package org.son.haruharu.domain.payment.implement;

public interface IapWebhookEventRepository {

    IapWebhookEvent save(IapWebhookEvent event);

    boolean existsByEventId(String eventId);
}
