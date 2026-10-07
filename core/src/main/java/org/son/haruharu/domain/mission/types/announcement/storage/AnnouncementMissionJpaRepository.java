package org.son.haruharu.domain.mission.types.announcement.storage;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnnouncementMissionJpaRepository extends JpaRepository<AnnouncementMissionEntity, Long> {
    @Query("select m.id as missionId, m.opensAt as opensAt, m.canceledAt as canceledAt, a.revealFields as revealFields "
            + "from AnnouncementMissionEntity a join RoomMissionEntity m on m.id = a.roomMissionId "
            + "where m.roomId = :roomId and a.revealFields is not null")
    List<RevealPlan> findRevealPlans(@Param("roomId") Long roomId);

    interface RevealPlan {
        Long getMissionId();
        LocalDateTime getOpensAt();
        LocalDateTime getCanceledAt();
        String getRevealFields();
    }
}
