package org.son.haruharu.domain.ticket.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.ticket.implement.TicketWallet;

@Entity
@Table(name = "ticket_wallet")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketWalletEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private long balance;

    public static TicketWalletEntity from(TicketWallet wallet) {
        var entity = new TicketWalletEntity();
        entity.id = wallet.id();
        entity.userId = wallet.userId();
        entity.balance = wallet.balance();
        return entity;
    }

    public TicketWallet toDomain() {
        return new TicketWallet(id, userId, balance, updatedAt);
    }
}
