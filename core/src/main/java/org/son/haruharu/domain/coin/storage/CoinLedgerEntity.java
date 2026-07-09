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

@Entity
@Table(name = "coin_ledger", indexes = {
        @Index(name = "idx_coin_ledger_user_created", columnList = "userId, createdAt")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoinLedgerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private long delta;

    // 이 항목 적용 직후의 총 잔액 스냅샷(거래별 잔액 표시용). 페이지네이션 중간에서도 정확한 잔액 제공.
    @Column(nullable = false)
    private long balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CoinBucket bucket;

    // length 40: UPLOAD_COMPLETION_REWARD(24자)가 기존 20을 초과해 지급 INSERT 전면 실패(#169).
    // enum 추가 시 회귀는 EnumColumnLengthIntegrationTest가 전수 검증한다. 기존 DB는 수동 ALTER 필요.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CoinLedgerReason reason;

    @Column(length = 40)
    private String refType;

    @Column(length = 40)
    private String refId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static CoinLedgerEntity from(CoinLedger ledger) {
        var entity = new CoinLedgerEntity();
        entity.id = ledger.id();
        entity.userId = ledger.userId();
        entity.delta = ledger.delta();
        entity.balanceAfter = ledger.balanceAfter();
        entity.bucket = ledger.bucket();
        entity.reason = ledger.reason();
        entity.refType = ledger.refType();
        entity.refId = ledger.refId();
        entity.createdAt = ledger.createdAt();
        return entity;
    }

    public CoinLedger toDomain() {
        return new CoinLedger(id, userId, delta, balanceAfter, bucket, reason, refType, refId, createdAt);
    }
}
