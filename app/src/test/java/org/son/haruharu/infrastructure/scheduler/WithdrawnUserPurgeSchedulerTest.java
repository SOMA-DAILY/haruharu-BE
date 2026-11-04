package org.son.haruharu.infrastructure.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.user.business.WithdrawnUserPurgeService;

import java.util.List;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class WithdrawnUserPurgeSchedulerTest {

    @Mock WithdrawnUserPurgeService service;

    @Test
    @DisplayName("대상 회원을 한 명씩 파기하고 한 명의 실패가 다음 회원을 막지 않는다")
    void givenOneFailingUser_whenScheduled_thenOthersStillPurged() {
        given(service.findDueUserIds(0L)).willReturn(List.of(7L, 8L, 9L));
        given(service.findDueUserIds(9L)).willReturn(List.of());
        given(service.purge(8L)).willThrow(new IllegalStateException("s3 unavailable"));
        given(service.purge(7L)).willReturn(Map.of("user_profiles", 1));
        given(service.purge(9L)).willReturn(Map.of());

        new WithdrawnUserPurgeScheduler(service).purgeExpiredWithdrawnUsers();

        then(service).should().purge(7L);
        then(service).should().purge(8L);
        then(service).should().purge(9L);
        then(service).should().findDueUserIds(9L);
    }

    @Test
    @DisplayName("대상이 없으면 파기를 호출하지 않는다")
    void givenNoDueUsers_whenScheduled_thenNoPurge() {
        given(service.findDueUserIds(0L)).willReturn(List.of());

        new WithdrawnUserPurgeScheduler(service).purgeExpiredWithdrawnUsers();

        then(service).should(never()).purge(org.mockito.ArgumentMatchers.anyLong());
    }
}
