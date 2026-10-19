package org.son.haruharu.domain.question.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuestionGrantJpaRepository extends JpaRepository<QuestionGrantEntity, Long> {
    Optional<QuestionGrantEntity> findByRoomIdAndUserIdAndDay(Long roomId, Long userId, int day);
}
