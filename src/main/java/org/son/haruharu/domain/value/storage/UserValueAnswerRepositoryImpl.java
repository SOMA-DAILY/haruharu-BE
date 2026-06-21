package org.son.haruharu.domain.value.storage;

import org.son.haruharu.domain.value.implement.UserValueAnswer;
import org.son.haruharu.domain.value.implement.UserValueAnswerRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class UserValueAnswerRepositoryImpl implements UserValueAnswerRepository {

    private final UserValueAnswerJpaRepository userValueAnswerJpaRepository;

    public UserValueAnswerRepositoryImpl(UserValueAnswerJpaRepository userValueAnswerJpaRepository) {
        this.userValueAnswerJpaRepository = userValueAnswerJpaRepository;
    }

    @Override
    public List<UserValueAnswer> findAllByUserId(UUID userId) {
        return userValueAnswerJpaRepository.findAllByUserId(userId)
                .stream()
                .map(UserValueAnswerEntity::toDomain)
                .toList();
    }

    @Override
    public UserValueAnswer save(UserValueAnswer userValueAnswer) {
        return userValueAnswerJpaRepository.save(UserValueAnswerEntity.from(userValueAnswer)).toDomain();
    }
}
