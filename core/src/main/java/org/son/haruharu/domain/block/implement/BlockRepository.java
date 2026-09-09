package org.son.haruharu.domain.block.implement;

import java.util.List;

public interface BlockRepository {

    Block save(Block block);

    /** 단방향: blocker가 target을 차단했는가(중복 차단 검사·알림 억제용). */
    boolean existsByBlockerAndBlocked(Long blockerUserId, Long blockedUserId);

    /** 양방향: 어느 한쪽이라도 차단했는가(1:1 접근·매칭 게이트용). */
    boolean existsBetween(Long userIdA, Long userIdB);

    /** 내가 차단한 대상 목록(FE 공통 필터·그룹채팅 미리보기/안읽음 제외용). */
    List<Long> findBlockedUserIdsByBlocker(Long blockerUserId);

    /** 나를 차단한 유저 목록(발신자 기준 알림 수신자 필터용). */
    List<Long> findBlockerUserIdsByBlocked(Long blockedUserId);

    /** 나와 차단 관계(방향 무관)인 유저 목록(목록성 API 배치 필터용 — N+1 방지). */
    List<Long> findRelatedUserIds(Long userId);

    /**
     * 차단 해제 — 물리 삭제. 전역 soft-delete를 쓰면 잔행이 UNIQUE(blocker, blocked)와 충돌해
     * 재차단이 막히므로 native로 지운다(chat hardDelete와 동일 패턴). 반환값은 삭제 행 수(0이면 미차단).
     */
    int hardDeleteByBlockerAndBlocked(Long blockerUserId, Long blockedUserId);
}
