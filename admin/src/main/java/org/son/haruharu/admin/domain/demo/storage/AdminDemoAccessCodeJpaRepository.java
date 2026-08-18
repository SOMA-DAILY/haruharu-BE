package org.son.haruharu.admin.domain.demo.storage;

import java.util.Optional;

import org.son.haruharu.domain.demoaccess.storage.DemoAccessCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** 어드민이 core 의 데모 접근 코드 엔티티를 직접 관리한다(#352, product/mission 패턴 동일). 단일 행 운영. */
public interface AdminDemoAccessCodeJpaRepository extends JpaRepository<DemoAccessCodeEntity, Long> {

    Optional<DemoAccessCodeEntity> findTopByOrderByIdDesc();
}
