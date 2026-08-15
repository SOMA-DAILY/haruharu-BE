package org.son.haruharu.domain.room.storage;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomInviteJpaRepository extends JpaRepository<RoomInviteEntity, Long> {

    Optional<RoomInviteEntity> findByCode(String code);

    /** 입장 정원(usedCount) 갱신 경쟁 방지용 비관적 락(#312). 동시 입장 시 코드 행을 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from RoomInviteEntity i where i.code = :code")
    Optional<RoomInviteEntity> findByCodeForUpdate(@Param("code") String code);
}
