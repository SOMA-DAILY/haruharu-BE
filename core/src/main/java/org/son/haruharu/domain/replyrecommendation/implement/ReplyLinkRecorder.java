package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatMessageReader;
import org.springframework.stereotype.Component;

/**
 * 저장된 실제 영상 답장을 노출과 잇는다(명세 10장). 클라이언트의 "전송 성공"이 아니라 서버에 저장된 메시지를 기준으로 한다.
 * 명시적 출처가 있으면 그 노출에 explicit, 없으면 전송 전 마지막 유효 노출에 inferred, 노출이 없었으면 unexposed 다.
 * 출처가 다른 사람 것이거나 세트·영상이 다르면 연결만 무효로 하고 이유를 남긴다(답장은 이미 저장됐다).
 */
@Component
@RequiredArgsConstructor
public class ReplyLinkRecorder {

    static final Duration EPISODE_WINDOW = Duration.ofHours(24);

    private final RecommendationFeedbackRepository feedback;
    private final RecommendationRepository recommendations;
    private final RecommendationCollectionPolicy collection;
    private final ChatMessageReader chatMessages;
    private final Clock clock;

    public void record(SavedVideoReply reply, Instant now) {
        Long viewerId = reply.viewerId();
        if (!collection.collectable(viewerId)) {
            return;
        }
        Instant sentAt = reply.sentAt() == null ? now : AiReplyJson.instant(reply.sentAt(), clock.getZone());
        SourceCheck source = check(reply, viewerId, reply.videoId());

        List<ReplyEpisode> open = feedback.findOpenEpisodes(viewerId, reply.videoId(),
                sentAt.minus(EPISODE_WINDOW), sentAt);
        // 명시적 출처의 노출 episode 가 있으면 explicit, 없으면(노출 사건이 오지 않음 등) 전송 전 마지막 노출에 inferred.
        ReplyEpisode target = source.valid()
                ? open.stream()
                        .filter(episode -> episode.sessionId().equals(reply.sourceSessionId())
                                && episode.setId().equals(reply.sourceSetId()))
                        .findFirst()
                        .orElse(null)
                : null;
        ReplyAttribution attribution = ReplyAttribution.EXPLICIT;
        if (target == null) {
            target = open.stream().max(Comparator.comparing(ReplyEpisode::lastImpressionAt)).orElse(null);
            attribution = target == null ? ReplyAttribution.UNEXPOSED : ReplyAttribution.INFERRED;
        }
        for (ReplyEpisode episode : open) {
            feedback.closeEpisode(episode.id(), reply.messageId(), sentAt,
                    episode == target ? attribution : ReplyAttribution.SUPERSEDED, now);
        }
        ReplyEpisode linked = target;
        feedback.insertReplyLink(new ReplyLink(reply.messageId(), viewerId, reply.videoId(), sentAt,
                clip(reply.sourceSetId(), 36), clip(reply.sourceCardId(), 64), clip(reply.sourceSessionId(), 36),
                source.status(), source.reason(), attribution, linked == null ? null : linked.id()), now);
        if (linked != null) {
            Optional<ChatMessage> message = chatMessages.findById(reply.messageId());
            message.ifPresent(saved -> feedback.saveReplySnapshot(linked.id(), saved.id(), saved.content(),
                    linked.provenance(), now));
        }
    }

    SourceCheck check(SavedVideoReply source, Long viewerId, Long videoId) {
        if (!source.hasSource()) {
            return SourceCheck.NONE;
        }
        Optional<RecommendationSet> set = recommendations.findSet(source.sourceSetId());
        if (set.isEmpty()) {
            return SourceCheck.invalid("UNKNOWN_SET");
        }
        if (!set.get().videoId().equals(videoId)) {
            return SourceCheck.invalid("VIDEO_MISMATCH");
        }
        if (!recommendations.viewerReceivedSet(viewerId, videoId, source.sourceSetId())) {
            return SourceCheck.invalid("OTHER_VIEWER");
        }
        if (source.sourceCardId() != null && !set.get().containsCard(source.sourceCardId())) {
            return SourceCheck.invalid("CARD_NOT_IN_SET");
        }
        if (!uuid(source.sourceSessionId())) {
            return SourceCheck.invalid("INVALID_SESSION");
        }
        return SourceCheck.VALID;
    }

    private static boolean uuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            UUID.fromString(value);
            return value.length() == 36;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String clip(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    record SourceCheck(ReplySourceStatus status, String reason) {
        static final SourceCheck NONE = new SourceCheck(ReplySourceStatus.NONE, null);
        static final SourceCheck VALID = new SourceCheck(ReplySourceStatus.VALID, null);

        static SourceCheck invalid(String reason) {
            return new SourceCheck(ReplySourceStatus.INVALID, reason);
        }

        boolean valid() {
            return status == ReplySourceStatus.VALID;
        }
    }
}
