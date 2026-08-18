package org.son.haruharu.domain.demoaccess.storage;

import java.util.Optional;

import org.son.haruharu.domain.demoaccess.implement.DemoAccessCode;
import org.son.haruharu.domain.demoaccess.implement.DemoAccessCodeRepository;
import org.springframework.stereotype.Repository;

@Repository
public class DemoAccessCodeRepositoryImpl implements DemoAccessCodeRepository {

    private final DemoAccessCodeJpaRepository demoAccessCodeJpaRepository;

    public DemoAccessCodeRepositoryImpl(DemoAccessCodeJpaRepository demoAccessCodeJpaRepository) {
        this.demoAccessCodeJpaRepository = demoAccessCodeJpaRepository;
    }

    @Override
    public Optional<DemoAccessCode> findCurrent() {
        return demoAccessCodeJpaRepository.findTopByOrderByIdDesc().map(DemoAccessCodeEntity::toDomain);
    }
}
