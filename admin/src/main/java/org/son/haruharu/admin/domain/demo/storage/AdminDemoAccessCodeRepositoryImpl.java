package org.son.haruharu.admin.domain.demo.storage;

import java.util.Optional;

import org.son.haruharu.admin.domain.demo.implement.AdminDemoAccessCodeRepository;
import org.son.haruharu.domain.demoaccess.implement.DemoAccessCode;
import org.son.haruharu.domain.demoaccess.storage.DemoAccessCodeEntity;
import org.springframework.stereotype.Repository;

@Repository
public class AdminDemoAccessCodeRepositoryImpl implements AdminDemoAccessCodeRepository {

    private final AdminDemoAccessCodeJpaRepository adminDemoAccessCodeJpaRepository;

    public AdminDemoAccessCodeRepositoryImpl(AdminDemoAccessCodeJpaRepository adminDemoAccessCodeJpaRepository) {
        this.adminDemoAccessCodeJpaRepository = adminDemoAccessCodeJpaRepository;
    }

    @Override
    public Optional<DemoAccessCode> findCurrent() {
        return adminDemoAccessCodeJpaRepository.findTopByOrderByIdDesc().map(DemoAccessCodeEntity::toDomain);
    }

    @Override
    public DemoAccessCode save(DemoAccessCode code) {
        // 단일 행 유지: 기존 행이 있으면 값만 in-place 갱신하고, 없으면 최초 생성한다.
        DemoAccessCodeEntity entity = adminDemoAccessCodeJpaRepository.findTopByOrderByIdDesc()
                .map(existing -> {
                    existing.updateCode(code.code());
                    return existing;
                })
                .orElseGet(() -> DemoAccessCodeEntity.from(code));
        return adminDemoAccessCodeJpaRepository.save(entity).toDomain();
    }
}
