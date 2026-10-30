package org.son.haruharu.domain.room.storage;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MemberRemovalJpaRepository extends JpaRepository<MemberRemovalEntity,Long> {
    boolean existsByRoomIdAndUserId(Long roomId,Long userId);
}
