package org.son.haruharu.domain.midparticipation.storage;
import java.util.*;
import java.time.LocalDateTime;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ParticipationInvitationJpaRepository extends JpaRepository<ParticipationInvitationEntity,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ParticipationInvitationEntity i where i.id=:id")
    Optional<ParticipationInvitationEntity> locked(@Param("id") Long id);
    @Query("select i from ParticipationInvitationEntity i where i.userId=:userId and (:before is null or i.id<:before) order by i.id desc")
    List<ParticipationInvitationEntity> forUser(@Param("userId") Long userId,@Param("before") Long before,Pageable pageable);
    List<ParticipationInvitationEntity> findByCampaignIdOrderByUserIdAsc(Long campaignId);
    @Query("select i from ParticipationInvitationEntity i where i.notifiedVersion<i.deliveryVersion and (i.notificationClaimedAt is null or i.notificationClaimVersion<>i.deliveryVersion or i.notificationClaimedAt<:staleBefore) order by i.deliveredAt,i.id")
    List<ParticipationInvitationEntity> pendingNotifications(@Param("staleBefore") LocalDateTime staleBefore,Pageable pageable);
    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update ParticipationInvitationEntity i set i.notificationClaimedAt=:now,i.notificationClaimVersion=:version where i.id=:id and i.deliveryVersion=:version and i.notifiedVersion<:version and (i.notificationClaimedAt is null or i.notificationClaimVersion<>:version or i.notificationClaimedAt<:staleBefore)")
    int claimNotification(@Param("id") Long id,@Param("version") int version,@Param("now") LocalDateTime now,@Param("staleBefore") LocalDateTime staleBefore);
    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update ParticipationInvitationEntity i set i.notifiedVersion=:version where i.id=:id and i.notifiedVersion<:version")
    int finishNotification(@Param("id") Long id,@Param("version") int version);
}
