package org.son.haruharu.domain.payment.business.dto;

import java.util.List;

public record ProductsResponse(
        List<ProductResponse> products
) {
}
