package org.son.haruharu.domain.replyrecommendation.business;

import java.time.Clock;
import java.time.Instant;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.chat.business.exception.ChatNotMemberException;
import org.son.haruharu.domain.replyrecommendation.business.command.RecommendationEventCommand;
import org.son.haruharu.domain.replyrecommendation.business.exception.RecommendationEventConflictException;
import org.son.haruharu.domain.replyrecommendation.business.exception.RecommendationEventForbiddenException;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationAccessJudge;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationCollectionPolicy;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationEvent;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationEventParser;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationEventRecorder;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationRequest;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationRequestStore;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationSet;
import org.son.haruharu.domain.replyrecommendation.implement.ReplyRecommendationProperties;
import org.son.haruharu.domain.replyrecommendation.implement.exception.InvalidRecommendationEventException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추천 노출·선택·입력 시작·닫기 사건(명세 10장). 본인이 받은 요청·세트에만 남길 수 있고(아니면 403),
 * 같은 event_id 의 같은 내용은 204, 다른 내용은 409 다. 실사용자 수집이 꺼져 있으면 QA 계정만 저장한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReplyRecommendationEventService {

    private final ReplyRecommendationProperties properties;
    private final RecommendationAccessJudge accessJudge;
    private final RecommendationRequestStore requests;
    private final RecommendationEventRecorder recorder;
    private final RecommendationCollectionPolicy collection;
    private final Clock clock;

    @Transactional
    public void record(Long roomId, Long videoId, Long viewerId, RecommendationEventCommand command) {
        if (!properties.enabledFor(roomId)) {
            return;
        }
        if (!accessJudge.isMemberOfVideoRoom(roomId, videoId, viewerId)) {
            throw new ChatNotMemberException();
        }
        Instant now = clock.instant();
        RecommendationEvent event = RecommendationEventParser.parse(command.toRaw(), viewerId, videoId,
                collection.provenanceOf(viewerId), now);
        RecommendationRequest request = requests.find(event.requestId())
                .filter(found -> found.isIssuedTo(viewerId, roomId, videoId, event.setId()))
                .orElseThrow(RecommendationEventForbiddenException::new);
        RecommendationSet set = requests.findSet(request.setId())
                .orElseThrow(RecommendationEventForbiddenException::new);
        if (event.cardId() != null && !set.containsCard(event.cardId())) {
            throw new InvalidRecommendationEventException("card_id");
        }
        if (!collection.collectable(viewerId)) {
            return;
        }
        if (recorder.record(event, roomId, now) == RecommendationEventRecorder.Outcome.CONFLICT) {
            throw new RecommendationEventConflictException();
        }
    }
}
