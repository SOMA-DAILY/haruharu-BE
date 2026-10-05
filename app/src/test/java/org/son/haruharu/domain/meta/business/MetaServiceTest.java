package org.son.haruharu.domain.meta.business;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.meta.business.dto.MetaResponse;

import static org.assertj.core.api.Assertions.assertThat;

class MetaServiceTest {

    @Test
    @DisplayName("메타 응답에 세션 정책 상수(day_start_hour=9, total_days=3)가 담긴다")
    void givenSessionPolicy_whenGetMeta_thenReturnsSessionConstants() {
        MetaService service = new MetaService();

        MetaResponse result = service.getMeta();

        assertThat(result.session().dayStartHour()).isEqualTo(9);
        assertThat(result.session().totalDays()).isEqualTo(3);
    }
}
