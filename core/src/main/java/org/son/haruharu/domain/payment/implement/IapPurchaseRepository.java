package org.son.haruharu.domain.payment.implement;

import java.util.Optional;

public interface IapPurchaseRepository {

    IapPurchase save(IapPurchase purchase);

    Optional<IapPurchase> findByStoreTransactionId(String storeTransactionId);
}
