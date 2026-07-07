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
    private final CoinProperties coinProperties;

    public CoinService(
            CoinWalletReader coinWalletReader,
            CoinWalletWriter coinWalletWriter,
            CoinLedgerReader coinLedgerReader,
            CoinLedgerWriter coinLedgerWriter,
            CoinProperties coinProperties
    ) {
        this.coinWalletReader = coinWalletReader;
        this.coinWalletWriter = coinWalletWriter;
        this.coinLedgerReader = coinLedgerReader;
        this.coinLedgerWriter = coinLedgerWriter;
        this.coinProperties = coinProperties;
    }

    /**
     * 첫 가입 보너스 지급. 유저당 1회만 지급되며(원장에 SIGNUP_BONUS 존재 시 skip), 재호출·재로그인에도 중복 지급되지 않는다.
     */
    @Transactional
    public void grantSignupBonus(Long userId) {
        if (coinLedgerReader.existsByUserIdAndReason(userId, CoinLedgerReason.SIGNUP_BONUS)) {
            return;
        }
        credit(CoinCreditCommand.signupBonus(userId, coinProperties.signupBonus()));
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
        CoinWallet credited = wallet.credit(command.bucket(), command.amount());
        coinWalletWriter.save(credited);
        coinLedgerWriter.append(CoinLedger.of(
                command.userId(), command.amount(), credited.totalBalance(), command.bucket(),
                command.reason(), command.refType(), command.refId(), LocalDateTime.now()));
    }

    /** 코인 소모(리워드 먼저). 차감된 버킷마다 원장 항목을 남긴다. balanceAfter는 각 항목 적용 후의 총 잔액(순차). */
    @Transactional
    public void spend(CoinSpendCommand command) {
        CoinWallet wallet = coinWalletReader.getByUserId(command.userId());
        CoinSpend spend = wallet.spendRewardFirst(command.amount());
        coinWalletWriter.save(spend.wallet());

        LocalDateTime now = LocalDateTime.now();
        long balanceAfter = wallet.totalBalance();
        if (spend.fromReward() > 0) {
            balanceAfter -= spend.fromReward();
            coinLedgerWriter.append(CoinLedger.of(
                    command.userId(), -spend.fromReward(), balanceAfter, CoinBucket.REWARD,
                    command.reason(), command.refType(), command.refId(), now));
        }
        if (spend.fromPaid() > 0) {
            balanceAfter -= spend.fromPaid();
            coinLedgerWriter.append(CoinLedger.of(
                    command.userId(), -spend.fromPaid(), balanceAfter, CoinBucket.PAID,
                    command.reason(), command.refType(), command.refId(), now));
        }
    }

    /**
     * 회원 탈퇴 시 구름 지갑 삭제(soft). 유저의 다른 데이터와 동일하게 지갑 행을 soft-delete한다.
     * 물리 삭제가 아니므로 원장·지갑 데이터는 결제 보관·환불 대응을 위해 그대로 남는다. 지갑이 없으면 무시.
     */
    @Transactional
    public void deleteWallet(Long userId) {
        coinWalletWriter.deleteByUserId(userId);
    }

    /** 코인 환불 회수. PAID 잔액에서 차감(음수 허용)하고 원장에 기록한다. */
    @Transactional
    public void revoke(CoinRevokeCommand command) {
        CoinWallet wallet = coinWalletReader.getByUserId(command.userId());
        CoinWallet revoked = wallet.revokePaid(command.amount());
        coinWalletWriter.save(revoked);
        coinLedgerWriter.append(CoinLedger.of(
                command.userId(), -command.amount(), revoked.totalBalance(), CoinBucket.PAID,
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
