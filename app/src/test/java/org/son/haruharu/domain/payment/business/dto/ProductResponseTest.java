package org.son.haruharu.domain.payment.business.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.payment.implement.IapProduct;
import org.son.haruharu.domain.payment.implement.IapStore;

import static org.assertj.core.api.Assertions.assertThat;

class ProductResponseTest {

    @Test
    @DisplayName("상품 응답에 기본 수량과 보너스 수량이 분리되어 담긴다(구름 12개 +1 카드 렌더링용)")
    void from_carriesCoinAmountAndBonusAmount() {
        IapProduct product = new IapProduct(1L, "coin_12", IapStore.APP_STORE, 12, 1, 1200, "KRW", true);

        ProductResponse response = ProductResponse.from(product);

        assertThat(response.coinAmount()).isEqualTo(12);
        assertThat(response.bonusAmount()).isEqualTo(1);
        assertThat(response.storeProductId()).isEqualTo("coin_12");
    }
}
