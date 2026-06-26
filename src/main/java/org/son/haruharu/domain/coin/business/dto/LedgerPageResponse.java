package org.son.haruharu.domain.coin.business.dto;

import java.util.List;

public record LedgerPageResponse(
        List<LedgerResponse> items,
        String nextCursor
) {
}
