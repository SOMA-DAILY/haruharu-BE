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

    /** 환불 처리 전용. 같은 거래의 환불 이벤트가 동시에 와도 한 번만 회수되게 구매 행을 잠근다(#492). */
    public Optional<IapPurchase> findByStoreTransactionIdForUpdate(String storeTransactionId) {
        return iapPurchaseRepository.findByStoreTransactionIdForUpdate(storeTransactionId);
    }
}
