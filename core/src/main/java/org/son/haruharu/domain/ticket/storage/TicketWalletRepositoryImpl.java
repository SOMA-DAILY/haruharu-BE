package org.son.haruharu.domain.ticket.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.ticket.implement.TicketWallet;
import org.son.haruharu.domain.ticket.implement.TicketWalletRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TicketWalletRepositoryImpl implements TicketWalletRepository {

    private final TicketWalletJpaRepository jpaRepository;

    @Override
    public Optional<TicketWallet> findByUserId(Long userId) {
        return jpaRepository.findByUserId(userId).map(TicketWalletEntity::toDomain);
    }

    @Override
    public Optional<TicketWallet> findByUserIdForUpdate(Long userId) {
        return jpaRepository.findByUserIdForUpdate(userId).map(TicketWalletEntity::toDomain);
    }

    @Override
    public boolean existsIncludingDeleted(Long userId) {
        return jpaRepository.countByUserIdIncludingDeleted(userId) > 0;
    }

    @Override
    public TicketWallet save(TicketWallet wallet) {
        return jpaRepository.save(TicketWalletEntity.from(wallet)).toDomain();
    }

    @Override
    public void deleteByUserId(Long userId) {
        jpaRepository.deleteByUserId(userId);
    }
}
