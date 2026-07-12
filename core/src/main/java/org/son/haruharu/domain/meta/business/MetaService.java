package org.son.haruharu.domain.meta.business;

import org.son.haruharu.domain.matching.business.MatchingProperties;
import org.son.haruharu.domain.meta.business.dto.MetaResponse;
import org.springframework.stereotype.Service;

/** 정책 상수 메타 조회. 설정값 매핑만 하므로 영속성 접근·트랜잭션이 없다. */
@Service
public class MetaService {

    private final MatchingProperties matchingProperties;

    public MetaService(MatchingProperties matchingProperties) {
        this.matchingProperties = matchingProperties;
    }

    public MetaResponse getMeta() {
        return new MetaResponse(new MetaResponse.MatchingMeta(matchingProperties.enterCost()));
    }
}
