package org.son.haruharu.domain.profile.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserProfileJpaRepository extends JpaRepository<UserProfileEntity, Long> {

    Optional<UserProfileEntity> findByUserId(Long userId);

    List<UserProfileEntity> findByUserIdIn(Collection<Long> userIds);

    void deleteByUserId(Long userId);

    // 기수 신청 연락처 3칸만 바꾼다(#511). 행 전체를 덮어쓰지 않아 동시에 들어온 다른 프로필 수정이 사라지지 않는다.
    // 벌크 UPDATE 는 Auditing 을 거치지 않으므로 updated_at 을 직접 적는다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update UserProfileEntity p
            set p.affiliation = :school, p.department = :department, p.phone = :phone, p.updatedAt = :now
            where p.userId = :userId
            """)
    int updateApplicantContact(@Param("userId") Long userId, @Param("school") String school,
                               @Param("department") String department, @Param("phone") String phone,
                               @Param("now") LocalDateTime now);
}
