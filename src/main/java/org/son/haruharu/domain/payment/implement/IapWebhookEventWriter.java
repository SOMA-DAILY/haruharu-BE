package org.son.haruharu.domain.payment.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IapWebhookEventWriter {

    private final IapWebhookEventRepository iapWebhookEventRepository;

    public IapWebhookEvent append(IapWebhookEvent event) {
        return iapWebhookEventRepository.save(event);
    }
}
