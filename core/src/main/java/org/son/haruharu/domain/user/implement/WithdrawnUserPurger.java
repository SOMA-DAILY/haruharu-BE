package org.son.haruharu.domain.user.implement;

import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 탈퇴 회원 1명의 개인정보를 파기한다(#555). 순서: S3 객체 → DB 행 → purged_at.
 * S3 삭제가 실패하면 예외로 끝나 DB 는 그대로 남고 다음 날 다시 시도한다(행을 먼저 지우면 키를 잃어 객체가 고아가 된다).
 */
@Component
public class WithdrawnUserPurger {

    private final WithdrawnUserPurgeRepository repository;
    private final S3StoragePort storage;

    public WithdrawnUserPurger(WithdrawnUserPurgeRepository repository, S3StoragePort storage) {
        this.repository = repository;
        this.storage = storage;
    }

    /** S3 DeleteObjects 는 요청당 키 1000개가 상한이다. */
    private static final int S3_DELETE_BATCH = 1000;

    public List<Long> findCandidates(LocalDateTime withdrawnBefore, long afterId, int limit) {
        return repository.findPurgeCandidates(withdrawnBefore, afterId, limit);
    }

    public Map<String, Integer> purge(Long userId, LocalDateTime now) {
        List<String> keys = new ArrayList<>(toKeys(repository.findVideoUrls(userId)));
        keys.addAll(repository.findChatPhotoKeys(userId));
        for (int i = 0; i < keys.size(); i += S3_DELETE_BATCH) {
            storage.deleteObjectsOrThrow(keys.subList(i, Math.min(i + S3_DELETE_BATCH, keys.size())));
        }
        Map<String, Integer> counts = repository.purgePersonalData(userId, now);
        repository.markPurged(userId, now);
        return counts;
    }

    /** 영상 URL 은 publicBaseUrl + key 로 저장돼 있다. 우리 버킷 밖 URL 은 건너뛴다. */
    private List<String> toKeys(List<String> urls) {
        String base = storage.publicBaseUrl();
        return urls.stream()
                .filter(url -> url != null && base != null && url.startsWith(base) && url.length() > base.length())
                .map(url -> url.substring(base.length()))
                .distinct()
                .toList();
    }
}
