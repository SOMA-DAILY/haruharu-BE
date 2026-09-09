package org.son.haruharu.domain.block.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.block.implement.Block;
import org.son.haruharu.domain.block.implement.BlockRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class BlockRepositoryImpl implements BlockRepository {

    private final BlockJpaRepository jpaRepository;

    @Override
    public Block save(Block block) {
        return jpaRepository.save(BlockEntity.from(block)).toDomain();
    }

    @Override
    public boolean existsByBlockerAndBlocked(Long blockerUserId, Long blockedUserId) {
        return jpaRepository.existsByBlockerUserIdAndBlockedUserId(blockerUserId, blockedUserId);
    }

    @Override
    public boolean existsBetween(Long userIdA, Long userIdB) {
        return jpaRepository.existsBetween(userIdA, userIdB);
    }

    @Override
    public List<Long> findBlockedUserIdsByBlocker(Long blockerUserId) {
        return jpaRepository.findBlockedUserIdsByBlocker(blockerUserId);
    }

    @Override
    public List<Long> findBlockerUserIdsByBlocked(Long blockedUserId) {
        return jpaRepository.findBlockerUserIdsByBlocked(blockedUserId);
    }

    @Override
    public int hardDeleteByBlockerAndBlocked(Long blockerUserId, Long blockedUserId) {
        return jpaRepository.hardDeleteByBlockerAndBlocked(blockerUserId, blockedUserId);
    }

    @Override
    public List<Long> findRelatedUserIds(Long userId) {
        // JPQL은 UNION을 지원하지 않아 두 방향을 각각 조회해 합친다(각각 인덱스 스캔 — 저렴).
        List<Long> related = new ArrayList<>(jpaRepository.findBlockedUserIdsByBlocker(userId));
        related.addAll(jpaRepository.findBlockerUserIdsByBlocked(userId));
        return related;
    }
}
