package org.son.haruharu.domain.ticket.business;

import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.ticket.business.command.TicketCreditCommand;
import org.son.haruharu.domain.ticket.business.command.TicketRevokeCommand;
import org.son.haruharu.domain.ticket.business.command.TicketSpendCommand;
import org.son.haruharu.domain.ticket.business.dto.LedgerPageResponse;
import org.son.haruharu.domain.ticket.business.dto.LedgerResponse;
import org.son.haruharu.domain.ticket.business.dto.WalletResponse;
import org.son.haruharu.domain.ticket.implement.TicketLedger;
import org.son.haruharu.domain.ticket.implement.TicketLedgerReader;
import org.son.haruharu.domain.ticket.implement.TicketLedgerReason;
import org.son.haruharu.domain.ticket.implement.TicketLedgerWriter;
import org.son.haruharu.domain.ticket.implement.TicketWallet;
import org.son.haruharu.domain.ticket.implement.TicketWalletReader;
import org.son.haruharu.domain.ticket.implement.TicketWalletWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@Transactional(readOnly = true)
public class TicketService {

    private final TicketWalletReader ticketWalletReader;
    private final TicketWalletWriter ticketWalletWriter;
    private final TicketLedgerReader ticketLedgerReader;
    private final TicketLedgerWriter ticketLedgerWriter;

    public TicketService(
            TicketWalletReader ticketWalletReader,
            TicketWalletWriter ticketWalletWriter,
            TicketLedgerReader ticketLedgerReader,
            TicketLedgerWriter ticketLedgerWriter
    ) {
        this.ticketWalletReader = ticketWalletReader;
        this.ticketWalletWriter = ticketWalletWriter;
        this.ticketLedgerReader = ticketLedgerReader;
        this.ticketLedgerWriter = ticketLedgerWriter;
    }

    /**
     * 지갑 생성(가입 시점). 이미 있으면 아무것도 하지 않는다(멱등).
     * <p>
     * 잔액 변경 경로(credit·spend·revoke)가 "지갑은 항상 존재한다"를 전제하므로(#298) 유저를 만드는
     * 모든 경로가 이걸 호출해야 한다.
     * <p>
     * 존재 확인에 일반 조회가 아니라 soft-delete 포함 조회를 쓴다 — user_id 유니크 제약은 죽은 행에도
     * 걸리므로, 탈퇴로 soft-delete된 지갑이 남은 계정에 일반 조회로 판단해 INSERT하면 제약 위반이 난다.
     */
    @Transactional
    public void createWallet(Long userId) {
        if (ticketWalletReader.existsIncludingDeleted(userId)) {
            return;
        }
        ticketWalletWriter.save(TicketWallet.init(userId));
    }

    /** 살아 있는 지갑이 있는지. 탈퇴했거나 없는 유저로 들어온 결제를 걸러내는 데 쓴다. */
    public boolean hasWallet(Long userId) {
        return ticketWalletReader.findByUserId(userId).isPresent();
    }

    /**
     * 실결제(IAP 구매) 이력 존재 여부(#277). 결제 원장이 있는 계정은 실사용자로 판별하기 위함이다.
     */
    public boolean hasPurchaseHistory(Long userId) {
        return ticketLedgerReader.existsByUserIdAndReason(userId, TicketLedgerReason.PURCHASE);
    }

    /** 티켓 잔액 조회. 지갑이 아직 없으면 0 잔액으로 응답한다. */
    public WalletResponse getWallet(Long userId) {
        TicketWallet wallet = ticketWalletReader.findByUserId(userId)
                .orElseGet(() -> TicketWallet.init(userId));
        return WalletResponse.from(wallet);
    }

    /**
     * 티켓 내역 조회(최신순 커서 페이징).
     * cursor는 Long으로 받는다 — 숫자가 아닌 값은 Controller 바인딩 단계에서 400으로 걸러진다(#304).
     * 응답의 nextCursor는 클라이언트 호환을 위해 String을 유지한다.
     */
    public LedgerPageResponse getLedger(Long userId, Long cursor, int size) {
        LocalDateTime before = resolveCursor(cursor);
        List<TicketLedger> rows = ticketLedgerReader.findPage(userId, before, size + 1);

        boolean hasMore = rows.size() > size;
        List<TicketLedger> page = hasMore ? rows.subList(0, size) : rows;
        String nextCursor = hasMore ? page.get(page.size() - 1).id().toString() : null;

        List<LedgerResponse> items = page.stream().map(LedgerResponse::from).toList();
        return new LedgerPageResponse(items, nextCursor);
    }

    /**
     * 티켓 적립. 지갑에 행 배타 락을 걸고 갱신한다(#298) — 같은 유저에 대한 동시 적립·사용이
     * 서로의 갱신을 덮어쓰지 못하게 한다. 지갑은 가입 시점에 생성되므로 없으면 불변식 위반이다.
     */
    @Transactional
    public void credit(TicketCreditCommand command) {
        TicketWallet wallet = ticketWalletReader.getByUserIdForUpdate(command.userId());
        TicketWallet credited = wallet.credit(command.amount());
        ticketWalletWriter.save(credited);
        ticketLedgerWriter.append(TicketLedger.of(
                command.userId(), command.amount(), credited.balance(),
                command.reason(), command.refType(), command.refId(), command.memo(), LocalDateTime.now()));
        logTicket("credit", command.userId(), command.amount(), credited.balance(),
                command.reason(), command.refType(), command.refId());
    }

    /** 티켓 사용. 잔액이 부족하면 예외로 거절한다. */
    @Transactional
    public void spend(TicketSpendCommand command) {
        TicketWallet wallet = ticketWalletReader.getByUserIdForUpdate(command.userId());
        TicketWallet spent = wallet.spend(command.amount());
        ticketWalletWriter.save(spent);
        ticketLedgerWriter.append(TicketLedger.of(
                command.userId(), -command.amount(), spent.balance(),
                TicketLedgerReason.USE, command.refType(), command.refId(), null, LocalDateTime.now()));
        logTicket("spend", command.userId(), -command.amount(), spent.balance(),
                TicketLedgerReason.USE, command.refType(), command.refId());
    }

    /**
     * 티켓 증감 감사 로그(#257). 분쟁·문의 대응 시 원장(ticket_ledger) 조회 없이 흐름을 추적하기 위함.
     * 원장에 남지 않는 실패 트랜잭션은 이 로그로도 안 남는다 — 성공한 증감의 시계열 추적이 목적.
     * userId 외 식별자는 남기지 않는다(PII). 운영진 메모도 자유 입력이라 남기지 않는다.
     * <p>
     * <b>원장 항목 1건당 로그 1줄</b>을 유지해(#300) delta·balance가 원장과 1:1로 대조되게 한다.
     */
    private void logTicket(String op, Long userId, long delta, long balanceAfter,
                           TicketLedgerReason reason, String refType, String refId) {
        log.info("ticket {} userId={} delta={} balance={} reason={} ref={}:{}",
                op, userId, delta, balanceAfter, reason,
                refType == null ? "-" : refType, refId == null ? "-" : refId);
    }

    /**
     * 회원 탈퇴 시 티켓 지갑 삭제(soft). 유저의 다른 데이터와 동일하게 지갑 행을 soft-delete한다.
     * 물리 삭제가 아니므로 원장·지갑 데이터는 결제 보관·환불 대응을 위해 그대로 남는다. 지갑이 없으면 무시.
     */
    @Transactional
    public void deleteWallet(Long userId) {
        long balance = ticketWalletReader.findByUserId(userId).map(TicketWallet::balance).orElse(0L);
        ticketWalletWriter.deleteByUserId(userId);
        // 탈퇴 시점 잔액을 남긴다 — 탈퇴 후 환불·잔액 문의가 들어오면 이 값이 근거가 된다.
        log.info("ticket wallet-delete userId={} balanceAtDelete={}", userId, balance);
    }

    /** 티켓 환불 회수. 잔액에서 차감하고 원장에 기록한다. 실제로 회수한 수량을 돌려준다. */
    @Transactional
    public long revoke(TicketRevokeCommand command) {
        TicketWallet wallet = ticketWalletReader.getByUserIdForUpdate(command.userId());
        TicketWallet revoked = wallet.revoke(command.amount());
        long revokedAmount = wallet.balance() - revoked.balance();
        ticketWalletWriter.save(revoked);
        ticketLedgerWriter.append(TicketLedger.of(
                command.userId(), -revokedAmount, revoked.balance(),
                TicketLedgerReason.REFUND_REVOKE, command.refType(), command.refId(), null, LocalDateTime.now()));
        // 환불 회수는 분쟁 대응이 가장 잦은 경로다 — 요청 수량과 실제 회수 수량이 다를 수 있어 둘 다 남긴다.
        logTicket("revoke", command.userId(), -revokedAmount, revoked.balance(),
                TicketLedgerReason.REFUND_REVOKE, command.refType(), command.refId());
        if (revokedAmount < command.amount()) {
            log.warn("ticket revoke-shortfall userId={} requested={} revoked={} ref={}:{}",
                    command.userId(), command.amount(), revokedAmount, command.refType(), command.refId());
        }
        return revokedAmount;
    }

    /** 커서 id로 기준 시각을 찾는다. 없는 id면 null을 반환해 첫 페이지로 되돌린다. */
    private LocalDateTime resolveCursor(Long cursor) {
        if (cursor == null) {
            return null;
        }
        return ticketLedgerReader.findById(cursor)
                .map(TicketLedger::createdAt)
                .orElse(null);
    }
}
