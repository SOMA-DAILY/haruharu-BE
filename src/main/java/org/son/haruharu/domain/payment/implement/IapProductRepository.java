package org.son.haruharu.domain.payment.implement;

import java.util.List;
import java.util.Optional;

public interface IapProductRepository {

    Optional<IapProduct> findByStoreProductId(String storeProductId);

    List<IapProduct> findAllByActiveTrue();
}
