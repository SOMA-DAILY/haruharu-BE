package org.son.haruharu.domain.block.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BlockJpaRepository extends JpaRepository<BlockEntity, Long> {

    boolean existsByBlockerUserIdAndBlockedUserId(Long blockerUserId, Long blockedUserId);

    @Query("select count(b) > 0 from BlockEntity b "
            + "where (b.blockerUserId = :a and b.blockedUserId = :b) "
            + "or (b.blockerUserId = :b and b.blockedUserId = :a)")
    boolean existsBetween(@Param("a") Long userIdA, @Param("b") Long userIdB);

    @Query("select b.blockedUserId from BlockEntity b where b.blockerUserId = :userId")
    List<Long> findBlockedUserIdsByBlocker(@Param("userId") Long userId);

    @Query("select b.blockerUserId from BlockEntity b where b.blockedUserId = :userId")
    List<Long> findBlockerUserIdsByBlocked(@Param("userId") Long userId);

    // 네이티브 DELETE — 전역 @SoftDelete 우회. soft-delete 잔행이 UNIQUE와 충돌해 재차단을 막지 않게 물리 삭제한다.
    @Modifying
    @Query(value = "delete from user_blocks where blocker_user_id = :blockerUserId and blocked_user_id = :blockedUserId",
            nativeQuery = true)
    int hardDeleteByBlockerAndBlocked(@Param("blockerUserId") Long blockerUserId,
                                      @Param("blockedUserId") Long blockedUserId);
}
