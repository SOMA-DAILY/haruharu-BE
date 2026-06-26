package org.son.haruharu.domain.payment.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IapPurchaseJpaRepository extends JpaRepository<IapPurchaseEntity, UUID> {

    Optional<IapPurchaseEntity> findByStoreTransactionId(String storeTransactionId);
}
