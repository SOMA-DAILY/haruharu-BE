package org.son.haruharu.domain.notification.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.notification.implement.PushBundle;
import org.son.haruharu.domain.notification.implement.PushBundleRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PushBundleRepositoryImpl implements PushBundleRepository {

    private final PushBundleJpaRepository jpaRepository;

    @Override
    public List<PushBundle> findAllForUpdate(Collection<Long> userIds, String bundleKey) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findAllByUserIdInAndBundleKeyOrderByUserId(userIds, bundleKey).stream()
                .map(PushBundleEntity::toDomain)
                .toList();
    }

    // 기존 행은 같은 트랜잭션에서 잠가 읽은 관리 엔티티를 고친다(생성 시각 보존). 새 행만 삽입한다.
    @Override
    public List<PushBundle> saveAll(Collection<PushBundle> bundles) {
        List<PushBundleEntity> entities = bundles.stream()
                .map(bundle -> bundle.id() == null
                        ? PushBundleEntity.from(bundle)
                        : jpaRepository.findById(bundle.id()).map(entity -> entity.update(bundle))
                                .orElseGet(() -> PushBundleEntity.from(bundle)))
                .toList();
        return jpaRepository.saveAll(entities).stream()
                .map(PushBundleEntity::toDomain)
                .toList();
    }
}
