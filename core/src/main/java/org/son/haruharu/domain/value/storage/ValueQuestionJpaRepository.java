package org.son.haruharu.domain.value.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ValueQuestionJpaRepository extends JpaRepository<ValueQuestionEntity, Long> {

    List<ValueQuestionEntity> findAllByIsActiveTrueOrderByDisplayOrderAsc();
}
