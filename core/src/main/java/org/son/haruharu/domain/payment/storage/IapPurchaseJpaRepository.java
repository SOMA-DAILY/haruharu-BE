package org.son.haruharu.domain.payment.storage;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IapPurchaseJpaRepository extends JpaRepository<IapPurchaseEntity, Long> {

    Optional<IapPurchaseEntity> findByStoreTransactionId(String storeTransactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from IapPurchaseEntity p where p.storeTransactionId = :storeTransactionId")
    Optional<IapPurchaseEntity> findByStoreTransactionIdForUpdate(@Param("storeTransactionId") String storeTransactionId);
}
