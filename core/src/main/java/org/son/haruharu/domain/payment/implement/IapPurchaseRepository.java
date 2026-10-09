package org.son.haruharu.domain.payment.implement;

import java.util.Optional;

public interface IapPurchaseRepository {

    IapPurchase save(IapPurchase purchase);

    Optional<IapPurchase> findByStoreTransactionId(String storeTransactionId);

    /** 환불 처리 전용 조회. 행 배타 락으로 같은 거래의 동시 환불 이벤트를 직렬화한다(#492). */
    Optional<IapPurchase> findByStoreTransactionIdForUpdate(String storeTransactionId);
}
