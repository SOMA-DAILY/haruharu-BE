package org.son.haruharu.domain.value.storage;

import org.son.haruharu.domain.value.implement.ValueAnswerUnlock;
import org.son.haruharu.domain.value.implement.ValueAnswerUnlockRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class ValueAnswerUnlockRepositoryImpl implements ValueAnswerUnlockRepository {

    private final ValueAnswerUnlockJpaRepository valueAnswerUnlockJpaRepository;

    public ValueAnswerUnlockRepositoryImpl(ValueAnswerUnlockJpaRepository valueAnswerUnlockJpaRepository) {
        this.valueAnswerUnlockJpaRepository = valueAnswerUnlockJpaRepository;
    }

    @Override
    public List<ValueAnswerUnlock> findByRoomMemberId(UUID roomMemberId) {
        return valueAnswerUnlockJpaRepository.findByRoomMemberId(roomMemberId)
                .stream()
                .map(ValueAnswerUnlockEntity::toDomain)
                .toList();
    }

    @Override
    public boolean exists(UUID roomMemberId, UUID userValueAnswerId) {
        return valueAnswerUnlockJpaRepository.existsByRoomMemberIdAndUserValueAnswerId(roomMemberId, userValueAnswerId);
    }

    @Override
    public ValueAnswerUnlock save(ValueAnswerUnlock unlock) {
        return valueAnswerUnlockJpaRepository.save(ValueAnswerUnlockEntity.from(unlock)).toDomain();
    }
}