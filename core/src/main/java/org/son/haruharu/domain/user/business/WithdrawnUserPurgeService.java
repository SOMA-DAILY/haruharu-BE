package org.son.haruharu.domain.user.business;

import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.user.implement.WithdrawnUserPurger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 탈퇴 후 90일이 지난 회원의 개인정보 파기 유스케이스(#555, 개인정보처리방침 제3조·제7조).
 * <p>
 * 90일 안에는 탈퇴 철회·신고 조사 대응으로 보관하고, 지나면 프로필·사진·영상·채팅 사진·본인확인 평문·기기 정보를
 * 물리 삭제하며 대화 본문·참여 이력은 식별자 없이 남긴다. users 행은 결제·분쟁 기록 연결용으로 5년 남는다.
 * 회원 1명이 트랜잭션 1개라 한 명의 실패가 다른 회원에 번지지 않고, purged_at 으로 재실행이 멱등이다.
 */
@Slf4j
@Service
public class WithdrawnUserPurgeService {

    public static final int RETENTION_DAYS = 90;
    public static final int BATCH_SIZE = 100;

    private final WithdrawnUserPurger withdrawnUserPurger;
    private final Clock clock;

    public WithdrawnUserPurgeService(WithdrawnUserPurger withdrawnUserPurger, Clock clock) {
        this.withdrawnUserPurger = withdrawnUserPurger;
        this.clock = clock;
    }

    /** 지금 기준으로 보유 기간이 끝난 회원 id 중 {@code afterId} 보다 큰 것 (최대 {@link #BATCH_SIZE}). */
    @Transactional(readOnly = true)
    public List<Long> findDueUserIds(long afterId) {
        return withdrawnUserPurger.findCandidates(LocalDateTime.now(clock).minusDays(RETENTION_DAYS), afterId, BATCH_SIZE);
    }

    /** 회원 1명 파기. 호출자(스케줄러)가 회원마다 따로 부르고 예외를 격리한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, Integer> purge(Long userId) {
        return withdrawnUserPurger.purge(userId, LocalDateTime.now(clock));
    }
}
