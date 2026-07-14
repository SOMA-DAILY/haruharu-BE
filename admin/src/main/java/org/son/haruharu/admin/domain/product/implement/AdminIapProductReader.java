package org.son.haruharu.admin.domain.product.implement;

import org.son.haruharu.admin.domain.product.storage.exception.AdminIapProductNotFoundException;
import org.son.haruharu.domain.payment.implement.IapProduct;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AdminIapProductReader {

    private final AdminIapProductRepository adminIapProductRepository;

    public AdminIapProductReader(AdminIapProductRepository adminIapProductRepository) {
        this.adminIapProductRepository = adminIapProductRepository;
    }

    public List<IapProduct> findAll() {
        return adminIapProductRepository.findAll();
    }

    public IapProduct getById(Long id) {
        return adminIapProductRepository.findById(id)
                .orElseThrow(AdminIapProductNotFoundException::new);
    }

    public boolean existsByStoreProductId(String storeProductId) {
        return adminIapProductRepository.existsByStoreProductId(storeProductId);
    }
}
