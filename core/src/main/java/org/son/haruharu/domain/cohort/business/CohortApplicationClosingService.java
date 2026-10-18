package org.son.haruharu.domain.cohort.business;

import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.cohort.implement.ApplicationCloseReason;
import org.son.haruharu.domain.cohort.implement.CohortApplication;
import org.son.haruharu.domain.cohort.implement.CohortApplicationStatus;
import org.son.haruharu.domain.cohort.implement.CohortApplicationWriter;
import org.son.haruharu.domain.ticket.business.TicketService;
import org.son.haruharu.domain.ticket.business.command.TicketCreditCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 배정 전 신청을 닫고 참가권을 돌려주는 한 경로(#511). 본인 취소·기수 취소가 쓰고, 정산·운영진 제외(#518)도 여기로 온다.
 * 조건부 UPDATE 로 이긴 쪽만 반환한다 — 동시에 두 경로가 닫아도 반환은 한 번이다.
 * 원장의 부분 유니크(ref_type, ref_id, reason)가 최종으로 막는다.
 */
@Slf4j
@Service
public class CohortApplicationClosingService {

    private final CohortApplicationWriter applicationWriter;
    private final TicketService ticketService;

    public CohortApplicationClosingService(CohortApplicationWriter applicationWriter, TicketService ticketService) {
        this.applicationWriter = applicationWriter;
        this.ticketService = ticketService;
    }

    /**
     * 닫았으면 true, 그 사이 다른 경로가 먼저 닫았으면(이미 배정·종료) false.
     * 탈퇴해 지갑이 지워진 신청자는 돌려줄 곳이 없어 반환 없이 닫는다 — 한 명 때문에 기수 취소 전체가 실패하지 않게.
     * 잠금 순서: 신청 → 지갑. 호출하는 쪽은 기수 → 신청 순서를 지킨다.
     */
    @Transactional
    public boolean closeBeforeAssignment(CohortApplication application, CohortApplicationStatus to,
                                         ApplicationCloseReason reason, LocalDateTime now) {
        boolean refundable = ticketService.hasWallet(application.userId());
        if (!applicationWriter.closeBeforeAssignment(application.id(), to, reason, refundable, now)) {
            return false;
        }
        if (refundable) {
            ticketService.credit(TicketCreditCommand.useRefund(application.userId(),
                    CohortApplication.TICKETS_PER_APPLICATION, CohortApplication.TICKET_REF_TYPE,
                    String.valueOf(application.id())));
        } else {
            log.warn("cohort-application close-without-refund applicationId={} userId={} reason=WALLET_DELETED",
                    application.id(), application.userId());
        }
        log.info("cohort-application close applicationId={} userId={} cohortId={} status={} reason={} refunded={}",
                application.id(), application.userId(), application.cohortId(), to, reason, refundable);
        return true;
    }
}
