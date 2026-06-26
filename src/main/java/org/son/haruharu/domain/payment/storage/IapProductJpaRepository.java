package org.son.haruharu.domain.payment.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IapProductJpaRepository extends JpaRepository<IapProductEntity, UUID> {

    Optional<IapProductEntity> findByStoreProductId(String storeProductId);

    List<IapProductEntity> findAllByActiveTrue();
}
