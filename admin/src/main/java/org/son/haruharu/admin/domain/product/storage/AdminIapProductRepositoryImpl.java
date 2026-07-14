package org.son.haruharu.admin.domain.product.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.admin.domain.product.implement.AdminIapProductRepository;
import org.son.haruharu.domain.payment.implement.IapProduct;
import org.son.haruharu.domain.payment.storage.IapProductEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 어드민 코인 상품 포트의 JPA 어댑터. Entity↔Domain 변환을 전담한다. */
@Repository
@RequiredArgsConstructor
public class AdminIapProductRepositoryImpl implements AdminIapProductRepository {

    private final AdminIapProductJpaRepository jpaRepository;

    @Override
    public List<IapProduct> findAll() {
        return jpaRepository.findAll().stream().map(IapProductEntity::toDomain).toList();
    }

    @Override
    public Optional<IapProduct> findById(Long id) {
        return jpaRepository.findById(id).map(IapProductEntity::toDomain);
    }

    @Override
    public boolean existsByStoreProductId(String storeProductId) {
        return jpaRepository.existsByStoreProductId(storeProductId);
    }

    @Override
    public IapProduct save(IapProduct product) {
        return jpaRepository.save(IapProductEntity.from(product)).toDomain();
    }
}
