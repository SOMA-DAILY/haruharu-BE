package org.son.haruharu.admin.domain.product.implement;

import org.son.haruharu.domain.payment.implement.IapProduct;

import java.util.List;
import java.util.Optional;

/** 어드민 티켓 상품 포트(도메인 계약). storage가 JPA로 구현한다. Entity 비의존. */
public interface AdminIapProductRepository {
    List<IapProduct> findAll();
    Optional<IapProduct> findById(Long id);
    boolean existsByStoreProductId(String storeProductId);
    IapProduct save(IapProduct product);
}
