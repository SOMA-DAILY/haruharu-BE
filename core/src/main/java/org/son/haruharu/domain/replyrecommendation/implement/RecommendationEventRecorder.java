package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Instant;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 사건을 한 번만 남기고 노출 episode 를 갱신한다. 같은 event_id 의 같은 내용은 중복, 다른 내용은 충돌이다. */
@Component
@RequiredArgsConstructor
public class RecommendationEventRecorder {

    public enum Outcome { RECORDED, DUPLICATE, CONFLICT }

    private final RecommendationFeedbackRepository feedback;

    public Outcome record(RecommendationEvent event, Long roomId, Instant now) {
        if (!feedback.insertEvent(event)) {
            String saved = feedback.findEventPayloadSha(event.eventId()).orElse(null);
            return event.payloadSha().equals(saved) ? Outcome.DUPLICATE : Outcome.CONFLICT;
        }
        feedback.touchEpisode(event, roomId, now);
        return Outcome.RECORDED;
    }
}
