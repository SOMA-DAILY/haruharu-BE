package org.son.haruharu.domain.room.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

// 최종선택·매칭 쿼리·포트는 #41에서 추가. #39는 테이블 생성을 위한 스켈레톤.
public interface FinalSelectionJpaRepository extends JpaRepository<FinalSelectionEntity, UUID> {
}
