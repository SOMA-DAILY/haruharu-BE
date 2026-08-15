package org.son.haruharu.domain.room.storage;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.room.implement.RoomInvite;
import org.son.haruharu.domain.room.implement.RoomInviteRepository;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RoomInviteRepositoryImpl implements RoomInviteRepository {

    private final RoomInviteJpaRepository jpaRepository;

    @Override
    public RoomInvite save(RoomInvite invite) {
        return jpaRepository.save(RoomInviteEntity.from(invite)).toDomain();
    }

    @Override
    public Optional<RoomInvite> findByCode(String code) {
        return jpaRepository.findByCode(code).map(RoomInviteEntity::toDomain);
    }

    @Override
    public Optional<RoomInvite> findByCodeForUpdate(String code) {
        return jpaRepository.findByCodeForUpdate(code).map(RoomInviteEntity::toDomain);
    }
}
