package org.son.haruharu.domain.demoaccess.storage;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DemoAccessCodeJpaRepository extends JpaRepository<DemoAccessCodeEntity, Long> {

    /** 단일 행 운영 — 가장 최근 행을 현재 코드로 본다. */
    Optional<DemoAccessCodeEntity> findTopByOrderByIdDesc();
}
