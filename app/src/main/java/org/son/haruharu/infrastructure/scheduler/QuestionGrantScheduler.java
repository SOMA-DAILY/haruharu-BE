package org.son.haruharu.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.question.business.QuestionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 앱 접속 여부와 무관하게 Day 2 질문권을 안내한다. 지급의 멱등성은 DB가 보장한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionGrantScheduler {
    private final QuestionService questionService;

    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    public void grantAvailableQuestions() {
        try {
            long cursor = 0;
            while (true) {
                var roomIds = questionService.grantCandidateRoomIds(cursor, 100);
                if (roomIds.isEmpty()) return;
                for (Long roomId : roomIds) {
                    try {
                        questionService.reconcileGrants(roomId);
                    } catch (Exception exception) {
                        log.warn("익명 질문권 방 처리 실패: {}", exception.getClass().getSimpleName());
                    }
                }
                cursor = roomIds.getLast();
            }
        } catch (Exception exception) {
            log.warn("익명 질문권 대상 조회 실패: {}", exception.getClass().getSimpleName());
        }
    }
}
