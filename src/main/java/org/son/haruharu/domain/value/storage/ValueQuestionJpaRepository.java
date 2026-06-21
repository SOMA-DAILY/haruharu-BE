package org.son.haruharu.domain.value.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ValueQuestionJpaRepository extends JpaRepository<ValueQuestionEntity, UUID> {

    List<ValueQuestionEntity> findAllByIsActiveTrueOrderByDisplayOrderAsc();
}
