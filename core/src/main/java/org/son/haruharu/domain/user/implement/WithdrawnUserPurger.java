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
        List<String> keys = new ArrayList<>(toKeys(userId, repository.findVideoUrls(userId)));
        keys.addAll(repository.findChatPhotoKeys(userId));
        for (int i = 0; i < keys.size(); i += S3_DELETE_BATCH) {
            storage.deleteObjectsOrThrow(keys.subList(i, Math.min(i + S3_DELETE_BATCH, keys.size())));
        }
        Map<String, Integer> counts = repository.purgePersonalData(userId, now);
        repository.markPurged(userId, now);
        return counts;
    }

    /**
     * 영상 URL 은 publicBaseUrl + key 로 저장돼 있다. 회원 업로드(users/)만 지우고 demo/ 같은 공용 시드와 외부 URL 은 남긴다.
     * 업로드 경로(/users/)인데 지금 기본 주소로 시작하지 않으면(주소 변경 전 행) 키를 확정할 수 없어 이 회원 파기를 보류한다.
     * 던지면 S3·DB 둘 다 손대지 않은 채 purged_at 이 비어 다음 날 재시도된다(S3 삭제 실패와 같은 경로).
     */
    private List<String> toKeys(Long userId, List<String> urls) {
        String base = storage.publicBaseUrl();
        List<String> keys = new ArrayList<>();
        for (String url : urls) {
            if (url.startsWith(base)) {
                String key = url.substring(base.length());
                if (key.startsWith("users/")) {
                    keys.add(key);
                }
            } else if (url.contains("/users/")) {
                throw new IllegalStateException("탈퇴 회원 파기 보류: 기본 주소와 다른 업로드 URL userId=" + userId);
            }
        }
        return keys.stream().distinct().toList();
    }
}
