package org.son.haruharu.domain.payment.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IapWebhookEventReader {

    private final IapWebhookEventRepository iapWebhookEventRepository;

    public boolean isProcessed(String eventId) {
        return iapWebhookEventRepository.existsByEventId(eventId);
    }
}
