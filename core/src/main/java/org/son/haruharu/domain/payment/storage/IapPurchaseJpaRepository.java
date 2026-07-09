package org.son.haruharu.domain.payment.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IapPurchaseJpaRepository extends JpaRepository<IapPurchaseEntity, Long> {

    Optional<IapPurchaseEntity> findByStoreTransactionId(String storeTransactionId);
}
