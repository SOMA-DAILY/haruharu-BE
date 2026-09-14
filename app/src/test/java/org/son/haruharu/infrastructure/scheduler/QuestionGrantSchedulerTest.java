package org.son.haruharu.infrastructure.scheduler;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.question.business.QuestionService;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class QuestionGrantSchedulerTest {
    @Mock QuestionService service;

    @Test
    @DisplayName("질문권 대상 방을 커서로 순회하고 한 방의 실패가 다음 방을 막지 않는다")
    void givenOneFailedRoom_whenScheduled_thenProcessesRemainingRooms() {
        given(service.grantCandidateRoomIds(0L, 100)).willReturn(List.of(7L, 8L));
        given(service.grantCandidateRoomIds(8L, 100)).willReturn(List.of());
        doThrow(new IllegalStateException("unavailable")).when(service).reconcileGrants(7L);
        new QuestionGrantScheduler(service).grantAvailableQuestions();
        then(service).should().reconcileGrants(7L);
        then(service).should().reconcileGrants(8L);
        then(service).should().grantCandidateRoomIds(8L, 100);
    }
}
