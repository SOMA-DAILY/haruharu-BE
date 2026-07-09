package org.son.haruharu.domain.meta.business;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.meta.business.dto.EnumOptionResponse;

import static org.assertj.core.api.Assertions.assertThat;

class MetaEnumServiceTest {

    private final MetaEnumService service = new MetaEnumService();

    @Test
    @DisplayName("선택지형 enum을 code+label 목록으로 반환한다")
    void givenCatalog_whenGetEnumOptions_thenReturnsCodeLabelPairs() {
        // when
        var options = service.getEnumOptions();

        // then
        assertThat(options).containsKeys("drinkingStatus", "smokingStatus");
        assertThat(options.get("drinkingStatus"))
                .containsExactly(
                        new EnumOptionResponse("NONE", "안 마심"),
                        new EnumOptionResponse("SOMETIMES", "가끔"),
                        new EnumOptionResponse("OFTEN", "자주"));
        assertThat(options.get("smokingStatus"))
                .contains(new EnumOptionResponse("E_CIGARETTE", "전자담배"));
    }
}
