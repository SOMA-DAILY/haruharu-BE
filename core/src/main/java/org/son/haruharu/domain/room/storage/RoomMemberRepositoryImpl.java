package org.son.haruharu.domain.room.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RoomMemberRepositoryImpl implements RoomMemberRepository {

    private final RoomMemberJpaRepository jpaRepository;

    @Override
    public List<RoomMember> saveAll(List<RoomMember> members) {
        return jpaRepository.saveAll(members.stream().map(RoomMemberEntity::from).toList()).stream()
                .map(RoomMemberEntity::toDomain)
                .toList();
    }

    @Override
    public RoomMember save(RoomMember member) {
        return jpaRepository.save(RoomMemberEntity.from(member)).toDomain();
    }

    @Override
    public List<RoomMember> findByUserId(Long userId) {
        return jpaRepository.findByUserId(userId).stream().map(RoomMemberEntity::toDomain).toList();
    }

    @Override
    public List<RoomMember> findByRoomId(Long roomId) {
        return jpaRepository.findByRoomId(roomId).stream().map(RoomMemberEntity::toDomain).toList();
    }

    @Override
    public List<RoomMember> findByRoomIds(Collection<Long> roomIds) {
        if (roomIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByRoomIdIn(roomIds).stream().map(RoomMemberEntity::toDomain).toList();
    }

    @Override
    public List<RoomMember> findByRoomIdOrderByIdAsc(Long roomId) {
        return jpaRepository.findByRoomIdOrderByIdAsc(roomId).stream()
                .map(RoomMemberEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<RoomMember> findByRoomIdAndUserId(Long roomId, Long userId) {
        return jpaRepository.findByRoomIdAndUserId(roomId, userId).map(RoomMemberEntity::toDomain);
    }

    @Override
    public long countByRoomId(Long roomId) {
        return jpaRepository.countByRoomId(roomId);
    }

    @Override
    public Optional<RoomMember> findById(Long id) {
        return jpaRepository.findById(id).map(RoomMemberEntity::toDomain);
    }
}
