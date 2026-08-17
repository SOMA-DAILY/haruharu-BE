package org.son.haruharu.domain.room.storage;

import org.son.haruharu.domain.room.implement.FinalSelection;
import org.son.haruharu.domain.room.implement.FinalSelectionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class FinalSelectionRepositoryImpl implements FinalSelectionRepository {

    private final FinalSelectionJpaRepository finalSelectionJpaRepository;

    public FinalSelectionRepositoryImpl(FinalSelectionJpaRepository finalSelectionJpaRepository) {
        this.finalSelectionJpaRepository = finalSelectionJpaRepository;
    }

    @Override
    public Optional<FinalSelection> findByRoomMemberId(Long roomMemberId) {
        return finalSelectionJpaRepository.findByRoomMemberId(roomMemberId).map(FinalSelectionEntity::toDomain);
    }

    @Override
    public boolean existsByRoomMemberId(Long roomMemberId) {
        return finalSelectionJpaRepository.existsByRoomMemberId(roomMemberId);
    }

    @Override
    public List<FinalSelection> findByRoomMemberIdIn(List<Long> roomMemberIds) {
        if (roomMemberIds.isEmpty()) {
            return List.of();
        }
        return finalSelectionJpaRepository.findByRoomMemberIdIn(roomMemberIds).stream()
                .map(FinalSelectionEntity::toDomain)
                .toList();
    }

    @Override
    public FinalSelection save(FinalSelection finalSelection) {
        return finalSelectionJpaRepository.save(FinalSelectionEntity.from(finalSelection)).toDomain();
    }
}
