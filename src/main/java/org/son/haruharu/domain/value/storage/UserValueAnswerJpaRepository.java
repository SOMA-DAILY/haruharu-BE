package org.son.haruharu.domain.value.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserValueAnswerJpaRepository extends JpaRepository<UserValueAnswerEntity, Long> {

    List<UserValueAnswerEntity> findAllByUserId(Long userId);
}
