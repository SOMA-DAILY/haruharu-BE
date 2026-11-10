package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 관찰 기간이 끝난 실제 영상 답장마다 Depth·Width 라벨을 한 번 남긴다(#559). 기간은 답장 저장 시각부터
 * {@code observation-hours} 이고, 기간이 끝나지 않은 답장은 계산하지 않는다. 기간 뒤 대화는 세지 않는다.
 * 이미 라벨이 있으면 건너뛰어(ON CONFLICT) 여러 서버가 겹쳐도 결과는 하나다.
 */
@Component
@RequiredArgsConstructor
public class ReplyDepthCollector {

    private final ReplyDepthRepository repository;
    private final ReplyDepthProperties properties;
    private final RecommendationCollectionPolicy collection;
    private final Clock clock;

    public boolean ready() {
        return repository.schemaReady();
    }

    public List<ReplyDepthTarget> findDue(Instant now) {
        return repository.findDueTargets(now.minus(window()), properties.batchSize());
    }

    /** 라벨을 넣었으면 true. 기간이 아직 안 끝났거나 이미 있으면 false. */
    public boolean observe(ReplyDepthTarget target, Instant now) {
        Optional<ReplyDepthRoot> root = repository.findRoot(target.messageId());
        Instant windowStart = root.map(found -> AiReplyJson.instant(found.createdAt(), clock.getZone()))
                .orElse(target.sentAt());
        Instant windowEnd = windowStart.plus(window());
        if (windowEnd.isAfter(now)) {
            return false;
        }
        ReplyDepthResult result;
        Boolean ownerReplied = null;
        Integer followupCount = null;
        if (root.isEmpty() || root.get().excluded()) {
            result = ReplyDepthResult.unobserved(ReplyDepthResult.REPLY_UNAVAILABLE);
        } else {
            Long roomId = root.get().roomId();
            LocalDateTime localEnd = root.get().createdAt().plus(window());
            List<ReplyDepthMessage> replies = repository.findRepliesAfter(roomId, target.messageId(), localEnd);
            result = ReplyDepthCalculator.calculate(target.messageId(), replies, properties.maxDepth());
            // 보조 지표(Depth·Width 와 따로): 작성자를 모르면 둘 다 비운다.
            Optional<Long> owner = repository.findVideoAuthor(target.videoId());
            if (owner.isPresent()) {
                if (result.status() == ReplyDepthStatus.OBSERVED) {
                    ownerReplied = ReplyDepthCalculator.ownerReplied(target.messageId(), replies, owner.get());
                }
                followupCount = repository.countMessagesAfter(roomId, target.messageId(), localEnd,
                        Set.copyOf(List.of(target.viewerId(), owner.get())));
            }
        }
        return repository.insert(new ReplyDepthObservation(target.messageId(), target.videoId(), target.viewerId(),
                root.map(ReplyDepthRoot::roomId).orElse(null), target.sourceSetId(), target.sourceCardId(), result,
                windowStart, windowEnd, now, provenanceOf(target), ownerReplied, followupCount));
    }

    /** 계속 실패하는 답장을 미관측(LABEL_FAILED)으로 닫는다. 채팅을 다시 읽지 않는다. 넣었으면 true. */
    public boolean closeAsFailed(ReplyDepthTarget target, Instant now) {
        return repository.insert(new ReplyDepthObservation(target.messageId(), target.videoId(), target.viewerId(),
                null, target.sourceSetId(), target.sourceCardId(),
                ReplyDepthResult.unobserved(ReplyDepthResult.LABEL_FAILED), target.sentAt(),
                target.sentAt().plus(window()), now, provenanceOf(target), null, null));
    }

    private String provenanceOf(ReplyDepthTarget target) {
        return target.provenance() != null ? target.provenance() : collection.provenanceOf(target.viewerId()).name();
    }

    private Duration window() {
        return Duration.ofHours(properties.observationHours());
    }
}
