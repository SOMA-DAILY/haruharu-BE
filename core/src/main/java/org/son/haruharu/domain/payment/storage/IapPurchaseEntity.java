package org.son.haruharu.domain.payment.storage;

import org.son.haruharu.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.son.haruharu.domain.payment.implement.IapPurchase;
import org.son.haruharu.domain.payment.implement.IapPurchaseStatus;
import org.son.haruharu.domain.payment.implement.IapStore;

import java.time.LocalDateTime;

@Entity
@Table(name = "iap_purchase", indexes = {
        @Index(name = "idx_iap_purchase_user", columnList = "userId")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IapPurchaseEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false, length = 100)
    private String storeProductId;

    @Column(nullable = false, unique = true, length = 100)
    private String storeTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IapStore store;

    @Column(nullable = false)
    private long ticketAmount;

    /** 환불됐지만 이미 사용해 회수하지 못한 티켓 수량. 환불 전이거나 전부 회수했으면 0. */
    @Column(nullable = false)
    @ColumnDefault("0")
    private long unrevokedTicketAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IapPurchaseStatus status;

    @Column(nullable = false)
    private LocalDateTime purchasedAt;

    @Column
    private LocalDateTime refundedAt;

    public static IapPurchaseEntity from(IapPurchase purchase) {
        var entity = new IapPurchaseEntity();
        entity.id = purchase.id();
        entity.userId = purchase.userId();
        entity.productId = purchase.productId();
        entity.storeProductId = purchase.storeProductId();
        entity.storeTransactionId = purchase.storeTransactionId();
        entity.store = purchase.store();
        entity.ticketAmount = purchase.ticketAmount();
        entity.unrevokedTicketAmount = purchase.unrevokedTicketAmount();
        entity.status = purchase.status();
        entity.purchasedAt = purchase.purchasedAt();
        entity.refundedAt = purchase.refundedAt();
        return entity;
    }

    public IapPurchase toDomain() {
        return new IapPurchase(id, userId, productId, storeProductId, storeTransactionId,
                store, ticketAmount, unrevokedTicketAmount, status, purchasedAt, refundedAt);
    }
}
