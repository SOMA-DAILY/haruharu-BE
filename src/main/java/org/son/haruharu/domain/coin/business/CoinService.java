package org.son.haruharu.domain.coin.business;

import org.son.haruharu.domain.coin.business.command.CoinCreditCommand;
import org.son.haruharu.domain.coin.business.command.CoinRevokeCommand;
import org.son.haruharu.domain.coin.business.command.CoinSpendCommand;
import org.son.haruharu.domain.coin.business.dto.LedgerPageResponse;
import org.son.haruharu.domain.coin.business.dto.LedgerResponse;
import org.son.haruharu.domain.coin.business.dto.WalletResponse;
import org.son.haruharu.domain.coin.implement.CoinBucket;
import org.son.haruharu.domain.coin.implement.CoinLedger;
import org.son.haruharu.domain.coin.implement.CoinLedgerReader;
import org.son.haruharu.domain.coin.implement.CoinLedgerReason;
import org.son.haruharu.domain.coin.implement.CoinLedgerWriter;
import org.son.haruharu.domain.coin.implement.CoinSpend;
import org.son.haruharu.domain.coin.implement.CoinWallet;
import org.son.haruharu.domain.coin.implement.CoinWalletReader;
import org.son.haruharu.domain.coin.implement.CoinWalletWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CoinService {

    private final CoinWalletReader coinWalletReader;
    private final CoinWalletWriter coinWalletWriter;
    private final CoinLedgerReader coinLedgerReader;
    private final CoinLedgerWriter coinLedgerWriter;

    public CoinService(
            CoinWalletReader coinWalletReader,
            CoinWalletWriter coinWalletWriter,
            CoinLedgerReader coinLedgerReader,
            CoinLedgerWriter coinLedgerWriter
    ) {
        this.coinWalletReader = coinWalletReader;
        this.coinWalletWriter = coinWalletWriter;
        this.coinLedgerReader = coinLedgerReader;
        this.coinLedgerWriter = coinLedgerWriter;
    }

    /** 코인 잔액 조회. 지갑이 아직 없으면 0 잔액으로 응답한다. */
    public WalletResponse getWallet(Long userId) {
        CoinWallet wallet = coinWalletReader.findByUserId(userId)
                .orElseGet(() -> CoinWallet.init(userId));
        return WalletResponse.from(wallet);
    }

    /** 코인 내역 조회(최신순 커서 페이징). */
    public LedgerPageResponse getLedger(Long userId, String cursor, int size) {
        LocalDateTime before = resolveCursor(cursor);
        List<CoinLedger> rows = coinLedgerReader.findPage(userId, before, size + 1);

        boolean hasMore = rows.size() > size;
        List<CoinLedger> page = hasMore ? rows.subList(0, size) : rows;
        String nextCursor = hasMore ? page.get(page.size() - 1).id().toString() : null;

        List<LedgerResponse> items = page.stream().map(LedgerResponse::from).toList();
        return new LedgerPageResponse(items, nextCursor);
    }

    /** 코인 적립. 지갑이 없으면 새로 생성해 적립한다. */
    @Transactional
    public void credit(CoinCreditCommand command) {
        CoinWallet wallet = coinWalletReader.findByUserId(command.userId())
                .orElseGet(() -> CoinWallet.init(command.userId()));
        coinWalletWriter.save(wallet.credit(command.bucket(), command.amount()));
        coinLedgerWriter.append(CoinLedger.of(
                command.userId(), command.amount(), command.bucket(),
                command.reason(), command.refType(), command.refId(), LocalDateTime.now()));
    }

    /** 코인 소모(리워드 먼저). 차감된 버킷마다 원장 항목을 남긴다. */
    @Transactional
    public void spend(CoinSpendCommand command) {
        CoinWallet wallet = coinWalletReader.getByUserId(command.userId());
        CoinSpend spend = wallet.spendRewardFirst(command.amount());
        coinWalletWriter.save(spend.wallet());

        LocalDateTime now = LocalDateTime.now();
        if (spend.fromReward() > 0) {
            coinLedgerWriter.append(CoinLedger.of(
                    command.userId(), -spend.fromReward(), CoinBucket.REWARD,
                    command.reason(), command.refType(), command.refId(), now));
        }
        if (spend.fromPaid() > 0) {
            coinLedgerWriter.append(CoinLedger.of(
                    command.userId(), -spend.fromPaid(), CoinBucket.PAID,
                    command.reason(), command.refType(), command.refId(), now));
        }
    }

    /** 코인 환불 회수. PAID 잔액에서 차감(음수 허용)하고 원장에 기록한다. */
    @Transactional
    public void revoke(CoinRevokeCommand command) {
        CoinWallet wallet = coinWalletReader.getByUserId(command.userId());
        coinWalletWriter.save(wallet.revokePaid(command.amount()));
        coinLedgerWriter.append(CoinLedger.of(
                command.userId(), -command.amount(), CoinBucket.PAID,
                CoinLedgerReason.REFUND_REVOKE, command.refType(), command.refId(), LocalDateTime.now()));
    }

    private LocalDateTime resolveCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        return coinLedgerReader.findById(Long.parseLong(cursor))
                .map(CoinLedger::createdAt)
                .orElse(null);
    }
}
