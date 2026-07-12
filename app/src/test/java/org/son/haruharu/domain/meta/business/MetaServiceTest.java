package org.son.haruharu.domain.meta.business;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.matching.business.MatchingProperties;
import org.son.haruharu.domain.meta.business.dto.MetaResponse;

import static org.assertj.core.api.Assertions.assertThat;

class MetaServiceTest {

    @Test
    @DisplayName("메타 응답에 매칭 참가 비용(enter_cost)이 설정값 그대로 담긴다")
    void givenEnterCost_whenGetMeta_thenReturnsConfiguredValue() {
        MetaService service = new MetaService(new MatchingProperties(20L));

        MetaResponse result = service.getMeta();

        assertThat(result.matching().enterCost()).isEqualTo(20L);
    }
}
