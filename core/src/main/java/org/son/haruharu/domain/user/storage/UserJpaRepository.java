package org.son.haruharu.domain.user.storage;

import java.util.Collection;
import java.util.Set;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

    @Query("select u.id from UserEntity u where u.id in :ids and u.status = org.son.haruharu.domain.user.implement.UserStatus.ACTIVE")
    Set<Long> findActiveIds(@Param("ids") Collection<Long> ids);

    /** 유저 행 비관적 쓰기 락(#321). 같은 userId 의 동시 방 입장을 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.id = :id")
    Optional<UserEntity> findByIdForUpdate(@Param("id") Long id);
}
