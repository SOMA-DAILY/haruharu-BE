package org.son.haruharu.domain.replyrecommendation.implement;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        ReplyResponseLabel response = ReplyResponseLabel.NONE;
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
                if (repository.responseSchemaReady()) {
                    response = response(root.get(), target, owner.get());
                }
            }
        }
        return repository.insert(new ReplyDepthObservation(target.messageId(), target.videoId(), target.viewerId(),
                root.map(ReplyDepthRoot::roomId).orElse(null), target.sourceSetId(), target.sourceCardId(), result,
                windowStart, windowEnd, now, provenanceOf(target), ownerReplied, followupCount,
                root.map(ReplyDepthRoot::cohortId).orElse(null), response));
    }

    /**
     * 효과 라벨(이슈 #560): 답장 뒤 {@code response-window-minutes} 안에 영상 작성자가 같은 채팅방에 말했는지와
     * 첫 메시지까지 분, 대조 확인용으로 다른 멤버 중 같은 창 안에 말한 수. Depth 와 섞지 않는다.
     */
    private ReplyResponseLabel response(ReplyDepthRoot root, ReplyDepthTarget target, Long owner) {
        int minutes = properties.responseWindowMinutes();
        LocalDateTime end = root.createdAt().plusMinutes(minutes);
        Optional<LocalDateTime> first = repository.findFirstMessageAt(root.roomId(), target.messageId(), root.createdAt(),
                end, owner);
        BigDecimal took = first.map(at -> BigDecimal.valueOf(Duration.between(root.createdAt(), at).toMillis())
                        .divide(BigDecimal.valueOf(60_000), 2, RoundingMode.HALF_UP))
                .orElse(null);
        ReplyBystanders bystanders = repository.countBystanders(root.roomId(), target.messageId(), root.createdAt(),
                end, Set.copyOf(List.of(target.viewerId(), owner)));
        return new ReplyResponseLabel(first.isPresent(), took, minutes, bystanders.members(), bystanders.responded());
    }

    /** 계속 실패하는 답장을 미관측(LABEL_FAILED)으로 닫는다. 채팅을 다시 읽지 않는다. 넣었으면 true. */
    public boolean closeAsFailed(ReplyDepthTarget target, Instant now) {
        return repository.insert(new ReplyDepthObservation(target.messageId(), target.videoId(), target.viewerId(),
                null, target.sourceSetId(), target.sourceCardId(),
                ReplyDepthResult.unobserved(ReplyDepthResult.LABEL_FAILED), target.sentAt(),
                target.sentAt().plus(window()), now, provenanceOf(target), null, null, null));
    }

    private String provenanceOf(ReplyDepthTarget target) {
        return target.provenance() != null ? target.provenance() : collection.provenanceOf(target.viewerId()).name();
    }

    private Duration window() {
        return Duration.ofHours(properties.observationHours());
    }
}
