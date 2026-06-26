package org.son.haruharu.domain.coin.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.coin.implement.CoinWallet;

import java.util.UUID;

@Entity
@Table(name = "coin_wallet")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoinWalletEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, unique = true, columnDefinition = "uuid")
    private UUID userId;

    @Column(nullable = false)
    private long paidBalance;

    @Column(nullable = false)
    private long rewardBalance;

    public static CoinWalletEntity from(CoinWallet wallet) {
        var entity = new CoinWalletEntity();
        entity.id = wallet.id();
        entity.userId = wallet.userId();
        entity.paidBalance = wallet.paidBalance();
        entity.rewardBalance = wallet.rewardBalance();
        return entity;
    }

    public CoinWallet toDomain() {
        return new CoinWallet(id, userId, paidBalance, rewardBalance, updatedAt);
    }
}
