package org.son.haruharu.domain.room.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

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
    public List<RoomMember> findByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId).stream().map(RoomMemberEntity::toDomain).toList();
    }

    @Override
    public List<RoomMember> findByRoomId(UUID roomId) {
        return jpaRepository.findByRoomId(roomId).stream().map(RoomMemberEntity::toDomain).toList();
    }
}
