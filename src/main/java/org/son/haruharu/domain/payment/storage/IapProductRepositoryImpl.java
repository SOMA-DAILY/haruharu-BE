package org.son.haruharu.domain.payment.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.payment.implement.IapProduct;
import org.son.haruharu.domain.payment.implement.IapProductRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class IapProductRepositoryImpl implements IapProductRepository {

    private final IapProductJpaRepository jpaRepository;

    @Override
    public Optional<IapProduct> findByStoreProductId(String storeProductId) {
        return jpaRepository.findByStoreProductId(storeProductId).map(IapProductEntity::toDomain);
    }

    @Override
    public List<IapProduct> findAllByActiveTrue() {
        return jpaRepository.findAllByActiveTrue().stream().map(IapProductEntity::toDomain).toList();
    }
}
