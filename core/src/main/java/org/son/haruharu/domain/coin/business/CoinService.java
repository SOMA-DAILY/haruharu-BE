package org.son.haruharu.domain.coin.business;

import lombok.extern.slf4j.Slf4j;
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

@Slf4j
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
     * 지갑 생성(가입 시점). 이미 있으면 아무것도 하지 않는다(멱등).
     * <p>
     * 잔액 변경 경로(credit·spend·revoke)가 "지갑은 항상 존재한다"를 전제하므로(#298) 유저를 만드는
     * 모든 경로가 이걸 호출해야 한다. 예전에는 첫 가입 보너스 지급의 부수효과로 지갑이 생겨서,
     * 보너스를 받지 않는 복귀 유저(#245)는 지갑 없이 시작했다.
     * <p>
     * 존재 확인에 일반 조회가 아니라 soft-delete 포함 조회를 쓴다 — user_id 유니크 제약은 죽은 행에도
     * 걸리므로, 탈퇴로 soft-delete된 지갑이 남은 계정에 일반 조회로 판단해 INSERT하면 제약 위반이 난다.
     */
    @Transactional
    public void createWallet(Long userId) {
        if (coinWalletReader.existsIncludingDeleted(userId)) {
            return;
        }
        coinWalletWriter.save(CoinWallet.init(userId));
    }

    /**
     * 첫 가입 보너스 지급. 유저당 1회만 지급되며(원장에 SIGNUP_BONUS 존재 시 skip), 재호출·재로그인에도 중복 지급되지 않는다.
     */
    @Transactional
    public void grantSignupBonus(Long userId) {
        if (!coinProperties.enabled()) {
            return; // 코인 비활성(#309 피벗) — 가입 보너스 미지급. 코드는 보존.
        }
        if (coinLedgerReader.existsByUserIdAndReason(userId, CoinLedgerReason.SIGNUP_BONUS)) {
            return;
        }
        credit(CoinCreditCommand.signupBonus(userId, coinProperties.signupBonus()));
    }

    /**
     * 실결제(IAP 구매) 이력 존재 여부(#277). 결제 원장이 있는 계정은 실사용자로 판별하기 위함이다.
     */
    public boolean hasPurchaseHistory(Long userId) {
        return coinLedgerReader.existsByUserIdAndReason(userId, CoinLedgerReason.PURCHASE);
    }

    /** 코인 잔액 조회. 지갑이 아직 없으면 0 잔액으로 응답한다. */
    public WalletResponse getWallet(Long userId) {
        CoinWallet wallet = coinWalletReader.findByUserId(userId)
                .orElseGet(() -> CoinWallet.init(userId));
        return WalletResponse.from(wallet);
    }

    /**
     * 코인 내역 조회(최신순 커서 페이징).
     * cursor는 Long으로 받는다 — 숫자가 아닌 값은 Controller 바인딩 단계에서 400으로 걸러진다(#304).
     * 응답의 nextCursor는 클라이언트 호환을 위해 String을 유지한다.
     */
    public LedgerPageResponse getLedger(Long userId, Long cursor, int size) {
        LocalDateTime before = resolveCursor(cursor);
        List<CoinLedger> rows = coinLedgerReader.findPage(userId, before, size + 1);

        boolean hasMore = rows.size() > size;
        List<CoinLedger> page = hasMore ? rows.subList(0, size) : rows;
        String nextCursor = hasMore ? page.get(page.size() - 1).id().toString() : null;

        List<LedgerResponse> items = page.stream().map(LedgerResponse::from).toList();
        return new LedgerPageResponse(items, nextCursor);
    }

    /**
     * 코인 적립. 지갑에 행 배타 락을 걸고 갱신한다(#298) — 같은 유저에 대한 동시 적립·소모가
     * 서로의 갱신을 덮어쓰지 못하게 한다. 지갑은 가입 시점에 생성되므로 없으면 불변식 위반이다.
     */
    @Transactional
    public void credit(CoinCreditCommand command) {
        CoinWallet wallet = coinWalletReader.getByUserIdForUpdate(command.userId());
        CoinWallet credited = wallet.credit(command.bucket(), command.amount());
        coinWalletWriter.save(credited);
        coinLedgerWriter.append(CoinLedger.of(
                command.userId(), command.amount(), credited.totalBalance(), command.bucket(),
                command.reason(), command.refType(), command.refId(), LocalDateTime.now()));
        logCoin("credit", command.userId(), command.amount(), credited.totalBalance(),
                command.bucket(), command.reason(), command.refType(), command.refId());
    }

    /** 코인 소모(리워드 먼저). 차감된 버킷마다 원장 항목을 남긴다. balanceAfter는 각 항목 적용 후의 총 잔액(순차). */
    @Transactional
    public void spend(CoinSpendCommand command) {
        CoinWallet wallet = coinWalletReader.getByUserIdForUpdate(command.userId());
        CoinSpend spend = wallet.spendRewardFirst(command.amount());
        coinWalletWriter.save(spend.wallet());

        LocalDateTime now = LocalDateTime.now();
        long balanceAfter = wallet.totalBalance();
        // 로그는 원장 항목마다 한 줄씩 남긴다 — 합산해서 한 줄로 남기면 원장의 개별 delta와 대조되지 않는다(#300).
        if (spend.fromReward() > 0) {
            balanceAfter -= spend.fromReward();
            coinLedgerWriter.append(CoinLedger.of(
                    command.userId(), -spend.fromReward(), balanceAfter, CoinBucket.REWARD,
                    command.reason(), command.refType(), command.refId(), now));
            logCoin("spend", command.userId(), -spend.fromReward(), balanceAfter,
                    CoinBucket.REWARD, command.reason(), command.refType(), command.refId());
        }
        if (spend.fromPaid() > 0) {
            balanceAfter -= spend.fromPaid();
            coinLedgerWriter.append(CoinLedger.of(
                    command.userId(), -spend.fromPaid(), balanceAfter, CoinBucket.PAID,
                    command.reason(), command.refType(), command.refId(), now));
            logCoin("spend", command.userId(), -spend.fromPaid(), balanceAfter,
                    CoinBucket.PAID, command.reason(), command.refType(), command.refId());
        }
    }

    /**
     * 구름 증감 감사 로그(#257). 분쟁·문의 대응 시 원장(coin_ledger) 조회 없이 흐름을 추적하기 위함.
     * 원장에 남지 않는 실패 트랜잭션은 이 로그로도 안 남는다 — 성공한 증감의 시계열 추적이 목적.
     * userId 외 식별자는 남기지 않는다(PII).
     * <p>
     * <b>원장 항목 1건당 로그 1줄</b>을 유지한다(#300). 소모처럼 버킷이 나뉘어 원장이 2건 남는 경우도
     * 합산하지 않고 각각 남겨야 delta·balance가 원장과 1:1로 대조된다.
     */
    private void logCoin(String op, Long userId, long delta, long balanceAfter,
                         CoinBucket bucket, CoinLedgerReason reason, String refType, String refId) {
        log.info("coin {} userId={} delta={} balance={} bucket={} reason={} ref={}:{}",
                op, userId, delta, balanceAfter, bucket == null ? "-" : bucket, reason,
                refType == null ? "-" : refType, refId == null ? "-" : refId);
    }

    /**
     * 회원 탈퇴 시 구름 지갑 삭제(soft). 유저의 다른 데이터와 동일하게 지갑 행을 soft-delete한다.
     * 물리 삭제가 아니므로 원장·지갑 데이터는 결제 보관·환불 대응을 위해 그대로 남는다. 지갑이 없으면 무시.
     */
    @Transactional
    public void deleteWallet(Long userId) {
        long balance = coinWalletReader.findByUserId(userId).map(CoinWallet::totalBalance).orElse(0L);
        coinWalletWriter.deleteByUserId(userId);
        // 탈퇴 시점 잔액을 남긴다 — 탈퇴 후 환불·잔액 문의가 들어오면 이 값이 근거가 된다.
        log.info("coin wallet-delete userId={} balanceAtDelete={}", userId, balance);
    }

    /** 코인 환불 회수. PAID 잔액에서 차감(음수 허용)하고 원장에 기록한다. */
    @Transactional
    public void revoke(CoinRevokeCommand command) {
        CoinWallet wallet = coinWalletReader.getByUserIdForUpdate(command.userId());
        CoinWallet revoked = wallet.revokePaid(command.amount());
        coinWalletWriter.save(revoked);
        coinLedgerWriter.append(CoinLedger.of(
                command.userId(), -command.amount(), revoked.totalBalance(), CoinBucket.PAID,
                CoinLedgerReason.REFUND_REVOKE, command.refType(), command.refId(), LocalDateTime.now()));
        // 환불 회수는 분쟁 대응이 가장 잦은 경로다 — 잔액이 음수가 될 수 있어 결과 잔액까지 남긴다(#300).
        logCoin("revoke", command.userId(), -command.amount(), revoked.totalBalance(),
                CoinBucket.PAID, CoinLedgerReason.REFUND_REVOKE, command.refType(), command.refId());
    }

    /** 커서 id로 기준 시각을 찾는다. 없는 id면 null을 반환해 첫 페이지로 되돌린다. */
    private LocalDateTime resolveCursor(Long cursor) {
        if (cursor == null) {
            return null;
        }
        return coinLedgerReader.findById(cursor)
                .map(CoinLedger::createdAt)
                .orElse(null);
    }
}
