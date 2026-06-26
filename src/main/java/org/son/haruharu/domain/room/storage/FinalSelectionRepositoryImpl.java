package org.son.haruharu.domain.room.storage;

import org.son.haruharu.domain.room.implement.FinalSelection;
import org.son.haruharu.domain.room.implement.FinalSelectionRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class FinalSelectionRepositoryImpl implements FinalSelectionRepository {

    private final FinalSelectionJpaRepository finalSelectionJpaRepository;

    public FinalSelectionRepositoryImpl(FinalSelectionJpaRepository finalSelectionJpaRepository) {
        this.finalSelectionJpaRepository = finalSelectionJpaRepository;
    }

    @Override
    public Optional<FinalSelection> findByRoomMemberId(UUID roomMemberId) {
        return finalSelectionJpaRepository.findByRoomMemberId(roomMemberId).map(FinalSelectionEntity::toDomain);
    }

    @Override
    public boolean existsByRoomMemberId(UUID roomMemberId) {
        return finalSelectionJpaRepository.existsByRoomMemberId(roomMemberId);
    }

    @Override
    public FinalSelection save(FinalSelection finalSelection) {
        return finalSelectionJpaRepository.save(FinalSelectionEntity.from(finalSelection)).toDomain();
    }
}
