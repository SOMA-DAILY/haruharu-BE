package org.son.haruharu.admin.domain.product.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.admin.domain.product.business.command.CreateIapProductCommand;
import org.son.haruharu.admin.domain.product.business.command.UpdateIapProductCommand;
import org.son.haruharu.admin.domain.product.business.dto.AdminIapProductResponse;
import org.son.haruharu.admin.domain.product.business.exception.AdminIapProductDuplicateException;
import org.son.haruharu.admin.domain.product.implement.AdminIapProductReader;
import org.son.haruharu.admin.domain.product.implement.AdminIapProductWriter;
import org.son.haruharu.admin.domain.product.storage.exception.AdminIapProductNotFoundException;
import org.son.haruharu.domain.payment.implement.IapProduct;
import org.son.haruharu.domain.payment.implement.IapStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminIapProductServiceTest {

    @Mock AdminIapProductReader adminIapProductReader;
    @Mock AdminIapProductWriter adminIapProductWriter;

    AdminIapProductService service;

    @BeforeEach
    void setUp() {
        service = new AdminIapProductService(adminIapProductReader, adminIapProductWriter);
    }

    private IapProduct product(Long id) {
        return new IapProduct(id, "groom_60", IapStore.APP_STORE, 60, 0, 9800, "KRW", true);
    }

    @Test
    @DisplayName("신규 storeProductId면 상품을 생성한다")
    void create_whenNew_saves() {
        given(adminIapProductReader.existsByStoreProductId("groom_60")).willReturn(false);
        given(adminIapProductWriter.save(any())).willReturn(product(1L));

        AdminIapProductResponse result = service.create(
                new CreateIapProductCommand("groom_60", IapStore.APP_STORE, 60, 0, 9800, "KRW"));

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.storeProductId()).isEqualTo("groom_60");
        assertThat(result.coinAmount()).isEqualTo(60);
    }

    @Test
    @DisplayName("이미 존재하는 storeProductId면 AdminIapProductDuplicateException(409)")
    void create_whenDuplicate_throws() {
        given(adminIapProductReader.existsByStoreProductId("groom_60")).willReturn(true);

        assertThatThrownBy(() -> service.create(
                new CreateIapProductCommand("groom_60", IapStore.APP_STORE, 60, 0, 9800, "KRW")))
                .isInstanceOf(AdminIapProductDuplicateException.class);
        verify(adminIapProductWriter, never()).save(any());
    }

    @Test
    @DisplayName("수정은 보낸 필드만 갱신하고 나머지는 유지한다")
    void update_partial_keepsUnchanged() {
        given(adminIapProductReader.getById(1L)).willReturn(product(1L));
        given(adminIapProductWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        // 가격만 변경
        AdminIapProductResponse result = service.update(1L,
                new UpdateIapProductCommand(null, null, 8900, null, null));

        assertThat(result.price()).isEqualTo(8900);
        assertThat(result.coinAmount()).isEqualTo(60); // 유지
        assertThat(result.storeProductId()).isEqualTo("groom_60"); // 불변
        assertThat(result.active()).isTrue(); // 유지
    }

    @Test
    @DisplayName("없는 상품 수정 시 AdminIapProductNotFoundException(404)")
    void update_whenNotFound_throws() {
        given(adminIapProductReader.getById(99L)).willThrow(new AdminIapProductNotFoundException());

        assertThatThrownBy(() -> service.update(99L,
                new UpdateIapProductCommand(null, null, 8900, null, null)))
                .isInstanceOf(AdminIapProductNotFoundException.class);
    }
}
