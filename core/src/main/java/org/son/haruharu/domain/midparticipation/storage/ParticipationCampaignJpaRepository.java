package org.son.haruharu.domain.midparticipation.storage;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ParticipationCampaignJpaRepository extends JpaRepository<ParticipationCampaignEntity,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ParticipationCampaignEntity c where c.id=:id")
    Optional<ParticipationCampaignEntity> locked(@Param("id") Long id);
    List<ParticipationCampaignEntity> findByRoomIdOrderByIdDesc(Long roomId,Pageable pageable);
}
