package org.son.haruharu.admin.domain.product.business;

import org.son.haruharu.admin.domain.product.business.command.CreateIapProductCommand;
import org.son.haruharu.admin.domain.product.business.command.UpdateIapProductCommand;
import org.son.haruharu.admin.domain.product.business.dto.AdminIapProductResponse;
import org.son.haruharu.admin.domain.product.business.exception.AdminIapProductDuplicateException;
import org.son.haruharu.admin.domain.product.implement.AdminIapProductReader;
import org.son.haruharu.admin.domain.product.implement.AdminIapProductWriter;
import org.son.haruharu.domain.payment.implement.IapProduct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 어드민 티켓 상품 카탈로그 관리. 생성·목록·수정(전부 SUPER 권한 — AdminSecurityConfig에서 제한). */
@Service
@Transactional(readOnly = true)
public class AdminIapProductService {

    private final AdminIapProductReader adminIapProductReader;
    private final AdminIapProductWriter adminIapProductWriter;

    public AdminIapProductService(AdminIapProductReader adminIapProductReader,
                                  AdminIapProductWriter adminIapProductWriter) {
        this.adminIapProductReader = adminIapProductReader;
        this.adminIapProductWriter = adminIapProductWriter;
    }

    public List<AdminIapProductResponse> getProducts() {
        return adminIapProductReader.findAll().stream().map(AdminIapProductResponse::from).toList();
    }

    @Transactional
    public AdminIapProductResponse create(CreateIapProductCommand command) {
        if (adminIapProductReader.existsByStoreProductId(command.storeProductId())) {
            throw new AdminIapProductDuplicateException();
        }
        IapProduct saved = adminIapProductWriter.save(IapProduct.create(
                command.storeProductId(), command.store(),
                command.ticketAmount(), command.price(), command.currency()));
        return AdminIapProductResponse.from(saved);
    }

    @Transactional
    public AdminIapProductResponse update(Long id, UpdateIapProductCommand command) {
        IapProduct existing = adminIapProductReader.getById(id);
        // 부분 수정: 제공된 필드만 갱신. storeProductId·store는 불변.
        IapProduct updated = new IapProduct(
                existing.id(), existing.storeProductId(), existing.store(),
                command.ticketAmount() != null ? command.ticketAmount() : existing.ticketAmount(),
                command.price() != null ? command.price() : existing.price(),
                command.currency() != null ? command.currency() : existing.currency(),
                command.active() != null ? command.active() : existing.active());
        return AdminIapProductResponse.from(adminIapProductWriter.save(updated));
    }
}
