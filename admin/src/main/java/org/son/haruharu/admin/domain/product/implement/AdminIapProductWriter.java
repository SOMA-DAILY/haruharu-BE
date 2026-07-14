package org.son.haruharu.admin.domain.product.implement;

import org.son.haruharu.domain.payment.implement.IapProduct;
import org.springframework.stereotype.Component;

@Component
public class AdminIapProductWriter {

    private final AdminIapProductRepository adminIapProductRepository;

    public AdminIapProductWriter(AdminIapProductRepository adminIapProductRepository) {
        this.adminIapProductRepository = adminIapProductRepository;
    }

    public IapProduct save(IapProduct product) {
        return adminIapProductRepository.save(product);
    }
}
