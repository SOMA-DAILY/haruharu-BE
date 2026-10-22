package org.son.haruharu.domain.room.storage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface InteractionGuideClaimJpaRepository extends JpaRepository<InteractionGuideClaimEntity, String> {
    List<InteractionGuideClaimEntity> findByRoomMemberId(Long roomMemberId);
}
