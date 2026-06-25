package org.son.haruharu.domain.matching.business;

import org.son.haruharu.domain.identity.business.IdentityService;
import org.son.haruharu.domain.matching.business.dto.QueueResponse;
import org.son.haruharu.domain.matching.business.exception.MatchingAlreadyQueuedException;
import org.son.haruharu.domain.matching.business.exception.MatchingNotVerifiedException;
import org.son.haruharu.domain.matching.implement.MatchingQueue;
import org.son.haruharu.domain.matching.implement.MatchingQueueReader;
import org.son.haruharu.domain.matching.implement.MatchingQueueWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class MatchingService {

    private final MatchingQueueReader matchingQueueReader;
    private final MatchingQueueWriter matchingQueueWriter;
    private final IdentityService identityService;

    public MatchingService(
        MatchingQueueReader matchingQueueReader,
        MatchingQueueWriter matchingQueueWriter,
        IdentityService identityService
    ) {
        this.matchingQueueReader = matchingQueueReader;
        this.matchingQueueWriter = matchingQueueWriter;
        this.identityService = identityService;
    }

    @Transactional
    public QueueResponse joinQueue(UUID userId) {
        // 성별은 본인인증(identity)에서만 온다. 미인증이면 성별이 없으므로 참가 차단.
        String gender = identityService.findVerifiedGender(userId)
            .orElseThrow(MatchingNotVerifiedException::new);
        if (matchingQueueReader.isQueued(userId)) {
            throw new MatchingAlreadyQueuedException();
        }
        MatchingQueue saved = matchingQueueWriter.save(MatchingQueue.join(userId, gender, LocalDateTime.now()));
        return QueueResponse.from(saved);
    }

    @Transactional
    public void leaveQueue(UUID userId) {
        MatchingQueue queued = matchingQueueReader.getQueued(userId);
        matchingQueueWriter.save(queued.cancelled());
    }
}
