package org.son.haruharu.domain.payment.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class IapPurchaseReader {

    private final IapPurchaseRepository iapPurchaseRepository;

    public Optional<IapPurchase> findByStoreTransactionId(String storeTransactionId) {
        return iapPurchaseRepository.findByStoreTransactionId(storeTransactionId);
    }
}
