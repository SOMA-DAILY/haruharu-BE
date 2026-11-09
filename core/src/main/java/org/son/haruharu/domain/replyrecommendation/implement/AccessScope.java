package org.son.haruharu.domain.replyrecommendation.implement;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 한 시청자가 볼 수 있는 방 기록의 범위. 시청자와 차단 관계인 방 멤버를 뺀다.
 * 차단이 없는 시청자는 모두 같은 범위(빈 목록)라 분석과 일반 추천을 함께 쓴다.
 */
public record AccessScope(List<Long> excludedActorIds, String hash) {

    public static AccessScope of(Collection<Long> excludedActorIds) {
        List<Long> sorted = excludedActorIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        return new AccessScope(sorted, AiReplyJson.sha256(AiReplyJson.write(sorted)));
    }

    public static AccessScope base() {
        return of(List.of());
    }

    public boolean excludes(Long userId) {
        return userId != null && excludedActorIds.contains(userId);
    }
}
