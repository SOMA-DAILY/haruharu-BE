package org.son.haruharu.domain.cohort.business;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.cohort.implement.ApplicationCloseReason;
import org.son.haruharu.domain.cohort.implement.CohortApplication;
import org.son.haruharu.domain.cohort.implement.CohortApplicationStatus;
import org.son.haruharu.domain.cohort.implement.CohortApplicationWriter;
import org.son.haruharu.domain.ticket.business.TicketService;
import org.son.haruharu.domain.ticket.business.command.TicketCreditCommand;
import org.son.haruharu.domain.ticket.implement.TicketLedgerReason;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CohortApplicationClosingServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 12, 0);
    private static final CohortApplication QUEUED = new CohortApplication(100L, 7L, 3L, 1L,
            CohortApplicationStatus.QUEUED, null, null, null, "한국대", "컴퓨터공학과", null, null, null, null, null,
            0, NOW, NOW, null, null, null, null, null, null, null, null, NOW, NOW);

    @Mock CohortApplicationWriter applicationWriter;
    @Mock TicketService ticketService;
    @InjectMocks CohortApplicationClosingService service;

    @Test
    @DisplayName("조건부 전이에 이기면 같은 신청을 출처로 참가권 1장을 사용 반환(USE_REFUND)한다")
    void givenTransitionWon_whenClose_thenRefundsOnce() {
        given(ticketService.hasWallet(7L)).willReturn(true);
        given(applicationWriter.closeBeforeAssignment(100L, CohortApplicationStatus.CANCELLED,
                ApplicationCloseReason.USER_CANCELLED, true, NOW)).willReturn(true);

        assertThat(service.closeBeforeAssignment(QUEUED, CohortApplicationStatus.CANCELLED,
                ApplicationCloseReason.USER_CANCELLED, NOW)).isTrue();

        verify(ticketService).credit(new TicketCreditCommand(7L, 1, TicketLedgerReason.USE_REFUND,
                "COHORT_APPLICATION", "100", null));
    }

    @Test
    @DisplayName("다른 경로가 먼저 닫았으면(전이 0건) 반환하지 않는다")
    void givenTransitionLost_whenClose_thenNoRefund() {
        given(ticketService.hasWallet(7L)).willReturn(true);
        given(applicationWriter.closeBeforeAssignment(100L, CohortApplicationStatus.NOT_ASSIGNED,
                ApplicationCloseReason.COHORT_CANCELLED, true, NOW)).willReturn(false);

        assertThat(service.closeBeforeAssignment(QUEUED, CohortApplicationStatus.NOT_ASSIGNED,
                ApplicationCloseReason.COHORT_CANCELLED, NOW)).isFalse();

        verify(ticketService, never()).credit(any());
    }

    @Test
    @DisplayName("탈퇴해 지갑이 없는 신청자는 반환 없이 닫는다(기수 취소가 한 명 때문에 실패하지 않게)")
    void givenWalletDeleted_whenClose_thenClosesWithoutRefund() {
        given(ticketService.hasWallet(7L)).willReturn(false);
        given(applicationWriter.closeBeforeAssignment(100L, CohortApplicationStatus.NOT_ASSIGNED,
                ApplicationCloseReason.COHORT_CANCELLED, false, NOW)).willReturn(true);

        assertThat(service.closeBeforeAssignment(QUEUED, CohortApplicationStatus.NOT_ASSIGNED,
                ApplicationCloseReason.COHORT_CANCELLED, NOW)).isTrue();

        verify(ticketService, never()).credit(any());
    }
}
