package org.son.haruharu.domain.user.implement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 탈퇴 후 보유 기간(90일)이 지난 회원의 개인정보를 물리 파기·가명처리하는 저장소 포트(#555).
 * <p>
 * 전역 소프트삭제(BaseEntity) 때문에 JPQL DELETE 는 deleted_at 만 찍고 끝나므로 물리 파기는 native 로만 된다.
 * 파기는 여러 도메인 테이블을 한 트랜잭션으로 지워야 멱등 경계가 서기 때문에, 도메인마다 포트를 늘리는 대신
 * 이 한 포트가 "탈퇴 회원 파기" 유스케이스의 SQL 을 모두 가진다. 다른 유스케이스는 이 포트를 쓰지 않는다.
 */
public interface WithdrawnUserPurgeRepository {

    /**
     * DELETED 전이(updated_at) 가 {@code withdrawnBefore} 이전이고 아직 파기 안 된 회원 id, {@code afterId} 초과 id 오름차순.
     * 커서라 계속 실패하는 회원이 한 번 실행 안에서 뒤 회원의 자리를 막지 않는다.
     */
    List<Long> findPurgeCandidates(LocalDateTime withdrawnBefore, long afterId, int limit);

    /** 회원이 올린 일상 영상의 viewer/feed/thumbnail URL (null 제외). S3 객체 삭제용. */
    List<String> findVideoUrls(Long userId);

    /** 회원이 보낸 채팅 사진의 S3 키. */
    List<String> findChatPhotoKeys(Long userId);

    /**
     * 행 파기와 가명처리를 한 번에 실행한다. 반환은 테이블(또는 작업)별 영향 행 수 — 로그용 숫자만, 개인정보 없음.
     * users 행, 신고, 참가권 원장·결제, CI/DI·소셜 자격(30일 스케줄러 몫) 은 건드리지 않는다.
     */
    Map<String, Integer> purgePersonalData(Long userId, LocalDateTime now);

    /** users.purged_at 을 찍어 다음 실행에서 제외한다. */
    void markPurged(Long userId, LocalDateTime purgedAt);
}
