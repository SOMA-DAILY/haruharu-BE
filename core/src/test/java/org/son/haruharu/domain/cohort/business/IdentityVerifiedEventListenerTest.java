package org.son.haruharu.domain.cohort.business;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.identity.business.event.IdentityVerifiedEvent;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IdentityVerifiedEventListenerTest {

    @Mock CohortApplicationScreeningService screeningService;
    @InjectMocks IdentityVerifiedEventListener listener;

    @Test
    @DisplayName("본인인증 커밋 후 그 유저의 PASS 대기 신청을 판정한다")
    void givenEvent_whenHandle_thenScreens() {
        listener.handle(new IdentityVerifiedEvent(7L));

        verify(screeningService).screenAfterIdentityVerified(7L);
    }

    @Test
    @DisplayName("판정이 실패해도 예외를 밖으로 내지 않는다 — PASS 결과는 이미 커밋됐고, 내 신청 조회가 다시 판정한다")
    void givenScreeningFails_whenHandle_thenSwallows() {
        given(screeningService.screenAfterIdentityVerified(7L)).willThrow(new IllegalStateException("db down"));

        assertThatCode(() -> listener.handle(new IdentityVerifiedEvent(7L))).doesNotThrowAnyException();
    }
}
