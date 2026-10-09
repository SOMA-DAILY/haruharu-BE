package org.son.haruharu.domain.ticket.storage;

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
import org.son.haruharu.domain.ticket.implement.TicketLedger;
import org.son.haruharu.domain.ticket.implement.TicketLedgerReason;

import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_ledger", indexes = {
        @Index(name = "idx_ticket_ledger_user_created", columnList = "userId, createdAt")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketLedgerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private long delta;

    // 이 항목 적용 직후의 잔액 스냅샷(거래별 잔액 표시용). 페이지네이션 중간에서도 정확한 잔액 제공.
    @Column(nullable = false)
    private long balanceAfter;

    // enum 추가 시 길이 회귀는 EnumColumnLengthIntegrationTest가 전수 검증한다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TicketLedgerReason reason;

    @Column(length = 40)
    private String refType;

    @Column(length = 40)
    private String refId;

    // 운영진 지급 사유 등 내부 기록. 유저 응답에는 싣지 않는다.
    @Column(length = 200)
    private String memo;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static TicketLedgerEntity from(TicketLedger ledger) {
        var entity = new TicketLedgerEntity();
        entity.id = ledger.id();
        entity.userId = ledger.userId();
        entity.delta = ledger.delta();
        entity.balanceAfter = ledger.balanceAfter();
        entity.reason = ledger.reason();
        entity.refType = ledger.refType();
        entity.refId = ledger.refId();
        entity.memo = ledger.memo();
        entity.createdAt = ledger.createdAt();
        return entity;
    }

    public TicketLedger toDomain() {
        return new TicketLedger(id, userId, delta, balanceAfter, reason, refType, refId, memo, createdAt);
    }
}
