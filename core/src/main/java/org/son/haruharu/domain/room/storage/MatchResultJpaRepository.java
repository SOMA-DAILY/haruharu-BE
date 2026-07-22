package org.son.haruharu.domain.room.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MatchResultJpaRepository extends JpaRepository<MatchResultEntity, Long> {

    Optional<MatchResultEntity> findByMemberAIdOrMemberBId(Long memberAId, Long memberBId);

    boolean existsByMemberAIdAndMemberBId(Long memberAId, Long memberBId);

    // 네이티브 DELETE — 전역 @SoftDelete가 JPQL 벌크 delete까지 soft로 바꾸므로, UNIQUE(member_a_id,member_b_id)를
    // 점유한 행을 실제로 지우려면 네이티브여야 한다(데모 제자리 리셋은 멤버를 재사용해 같은 쌍이 다시 들어옴).
    @Modifying
    @Query(value = "delete from match_results where member_a_id in (:memberIds) or member_b_id in (:memberIds)",
            nativeQuery = true)
    void hardDeleteByMemberIds(@Param("memberIds") java.util.List<Long> memberIds);
}
