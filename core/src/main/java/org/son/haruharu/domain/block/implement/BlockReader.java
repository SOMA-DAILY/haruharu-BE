package org.son.haruharu.domain.block.implement;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class BlockReader {

    private final BlockRepository blockRepository;

    public BlockReader(BlockRepository blockRepository) {
        this.blockRepository = blockRepository;
    }

    /** blocker가 target을 이미 차단했는지(유저·대상당 1회 정책, 알림 억제). */
    public boolean hasBlocked(Long blockerUserId, Long targetUserId) {
        return blockRepository.existsByBlockerAndBlocked(blockerUserId, targetUserId);
    }

    /** 어느 한쪽이라도 차단한 관계인지(1:1 접근·매칭은 양방향 제한). */
    public boolean isBlockedBetween(Long userIdA, Long userIdB) {
        return blockRepository.existsBetween(userIdA, userIdB);
    }

    /** 내가 차단한 대상 집합. 목록성 로직은 이걸 한 번 받아 인메모리 필터한다(루프 내 단건 조회 금지). */
    public Set<Long> getBlockedUserIds(Long blockerUserId) {
        return Set.copyOf(blockRepository.findBlockedUserIdsByBlocker(blockerUserId));
    }

    /** 발신자를 차단한 유저 집합(알림 수신자 배치 필터용). */
    public Set<Long> getBlockerUserIds(Long blockedUserId) {
        return Set.copyOf(blockRepository.findBlockerUserIdsByBlocked(blockedUserId));
    }

    /** 나와 차단 관계(방향 무관)인 유저 집합(채팅방 목록 등 양방향 배치 필터용). */
    public Set<Long> getRelatedUserIds(Long userId) {
        return Set.copyOf(blockRepository.findRelatedUserIds(userId));
    }
}
