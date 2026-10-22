package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.room.business.command.ClaimInteractionGuideCommand;
import org.son.haruharu.domain.room.business.dto.InteractionGuidanceResponse;
import org.son.haruharu.domain.room.business.dto.InteractionGuideClaimResponse;
import org.son.haruharu.domain.room.implement.InteractionGuidanceManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
@RequiredArgsConstructor
@Transactional
public class InteractionGuidanceService {
    private final InteractionGuidanceManager manager;
    private final Clock clock;

    /** AFTER_COMMIT에서 실제 저장하려면 시작 트랜잭션의 종료 자원과 분리해야 한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enroll(Long roomId) {
        manager.enroll(roomId, LocalDateTime.now(clock));
    }

    public InteractionGuidanceResponse get(Long roomId, Long userId) {
        var state = manager.get(roomId, userId, LocalDateTime.now(clock));
        var progress = state.progress();
        return new InteractionGuidanceResponse(state.experimentKey(), state.variant(), state.assignedAt(),
                state.now(), state.eligibleAt(), progress.firstReactedAt(), progress.firstVideoRepliedAt(),
                progress.reactionGuideConsumedAt(), progress.replyGuideConsumedAt(), state.nextGuide());
    }

    public InteractionGuideClaimResponse claim(Long roomId, Long userId, ClaimInteractionGuideCommand command) {
        var result = manager.claim(roomId, userId, command.kind(), command.videoPostId(),
                command.viewerSessionId(), LocalDateTime.now(clock));
        return result.claim() == null ? InteractionGuideClaimResponse.denied(result.kind(), result.reason())
                : InteractionGuideClaimResponse.accepted(result.claim());
    }
}
