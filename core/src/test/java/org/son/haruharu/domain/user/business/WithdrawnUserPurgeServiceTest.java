package org.son.haruharu.domain.user.business;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.user.implement.WithdrawnUserPurger;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class WithdrawnUserPurgeServiceTest {

    @Mock WithdrawnUserPurger purger;

    @Test
    @DisplayName("파기 대상은 지금으로부터 90일 전 이전에 탈퇴한 회원이며 한 번에 100명까지 본다")
    void givenClock_whenFindDueUserIds_thenThresholdIsNinetyDaysAgo() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-04T04:45:00Z"), ZoneId.of("Asia/Seoul"));
        LocalDateTime expectedThreshold = LocalDateTime.of(2026, 11, 4, 13, 45).minusDays(90);
        given(purger.findCandidates(expectedThreshold, 0L, 100)).willReturn(List.of(3L));

        List<Long> due = new WithdrawnUserPurgeService(purger, clock).findDueUserIds(0L);

        assertThat(due).containsExactly(3L);
        then(purger).should().findCandidates(expectedThreshold, 0L, 100);
    }
}
