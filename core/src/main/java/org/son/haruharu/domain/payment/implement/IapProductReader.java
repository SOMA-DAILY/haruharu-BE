package org.son.haruharu.domain.payment.implement;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.payment.storage.exception.IapProductNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class IapProductReader {

    private final IapProductRepository iapProductRepository;

    public IapProduct getByStoreProductId(String storeProductId) {
        return iapProductRepository.findByStoreProductId(storeProductId)
                .orElseThrow(() -> new IapProductNotFoundException(storeProductId));
    }

    public List<IapProduct> findAllActive() {
        return iapProductRepository.findAllByActiveTrue();
    }
}
