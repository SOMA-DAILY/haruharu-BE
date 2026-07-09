package org.son.haruharu.domain.payment.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IapPurchaseWriter {

    private final IapPurchaseRepository iapPurchaseRepository;

    public IapPurchase save(IapPurchase purchase) {
        return iapPurchaseRepository.save(purchase);
    }
}
