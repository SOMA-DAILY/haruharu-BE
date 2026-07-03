package org.son.haruharu.domain.coin.storage;

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
import org.son.haruharu.domain.coin.implement.CoinBucket;
import org.son.haruharu.domain.coin.implement.CoinLedger;
import org.son.haruharu.domain.coin.implement.CoinLedgerReason;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "coin_ledger", indexes = {
        @Index(name = "idx_coin_ledger_user_created", columnList = "userId, createdAt")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoinLedgerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private long delta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CoinBucket bucket;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CoinLedgerReason reason;

    @Column(length = 20)
    private String refType;

    @Column(columnDefinition = "uuid")
    private UUID refId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static CoinLedgerEntity from(CoinLedger ledger) {
        var entity = new CoinLedgerEntity();
        entity.id = ledger.id();
        entity.userId = ledger.userId();
        entity.delta = ledger.delta();
        entity.bucket = ledger.bucket();
        entity.reason = ledger.reason();
        entity.refType = ledger.refType();
        entity.refId = ledger.refId();
        entity.createdAt = ledger.createdAt();
        return entity;
    }

    public CoinLedger toDomain() {
        return new CoinLedger(id, userId, delta, bucket, reason, refType, refId, createdAt);
    }
}
