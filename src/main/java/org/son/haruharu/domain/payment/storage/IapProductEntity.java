package org.son.haruharu.domain.payment.storage;

import org.son.haruharu.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.domain.payment.implement.IapProduct;
import org.son.haruharu.domain.payment.implement.IapStore;

import java.util.UUID;

@Entity
@Table(name = "iap_product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IapProductEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String storeProductId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IapStore store;

    @Column(nullable = false)
    private long coinAmount;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(nullable = false)
    private boolean active;

    public static IapProductEntity from(IapProduct product) {
        var entity = new IapProductEntity();
        entity.id = product.id();
        entity.storeProductId = product.storeProductId();
        entity.store = product.store();
        entity.coinAmount = product.coinAmount();
        entity.price = product.price();
        entity.currency = product.currency();
        entity.active = product.active();
        return entity;
    }

    public IapProduct toDomain() {
        return new IapProduct(id, storeProductId, store, coinAmount, price, currency, active);
    }
}
