package org.son.haruharu.domain.video.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

// 반응 토글 쿼리·포트는 #40에서 추가. #39는 테이블 생성을 위한 스켈레톤.
public interface VideoReactionJpaRepository extends JpaRepository<VideoReactionEntity, UUID> {
}
