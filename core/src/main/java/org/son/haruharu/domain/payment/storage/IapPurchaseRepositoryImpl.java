package org.son.haruharu.domain.payment.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.payment.implement.IapPurchase;
import org.son.haruharu.domain.payment.implement.IapPurchaseRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class IapPurchaseRepositoryImpl implements IapPurchaseRepository {

    private final IapPurchaseJpaRepository jpaRepository;

    @Override
    public IapPurchase save(IapPurchase purchase) {
        return jpaRepository.save(IapPurchaseEntity.from(purchase)).toDomain();
    }

    @Override
    public Optional<IapPurchase> findByStoreTransactionId(String storeTransactionId) {
        return jpaRepository.findByStoreTransactionId(storeTransactionId).map(IapPurchaseEntity::toDomain);
    }

    @Override
    public Optional<IapPurchase> findByStoreTransactionIdForUpdate(String storeTransactionId) {
        return jpaRepository.findByStoreTransactionIdForUpdate(storeTransactionId).map(IapPurchaseEntity::toDomain);
    }
}
