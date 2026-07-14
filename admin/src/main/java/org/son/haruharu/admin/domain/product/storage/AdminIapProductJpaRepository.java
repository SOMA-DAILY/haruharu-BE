package org.son.haruharu.admin.domain.product.storage;

import org.son.haruharu.domain.payment.storage.IapProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminIapProductJpaRepository extends JpaRepository<IapProductEntity, Long> {
    boolean existsByStoreProductId(String storeProductId);
}
