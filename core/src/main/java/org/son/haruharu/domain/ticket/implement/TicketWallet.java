package org.son.haruharu.domain.ticket.implement;

import org.son.haruharu.domain.ticket.implement.exception.InsufficientTicketException;
import org.son.haruharu.domain.ticket.implement.exception.InvalidTicketAmountException;

import java.time.LocalDateTime;

/**
 * 티켓 지갑. 티켓은 한 종류라 잔액도 하나다(#492).
 * 모든 변경은 새 인스턴스를 반환한다(불변). updatedAt은 영속 계층(Auditing)이 관리한다.
 */
public record TicketWallet(
        Long id,
        Long userId,
        long balance,
        LocalDateTime updatedAt
) {

    /** 신규 지갑: 잔액 0. id/updatedAt은 영속화 시점에 부여된다. */
    public static TicketWallet init(Long userId) {
        return new TicketWallet(null, userId, 0L, null);
    }

    /** 적립: 티켓을 더한 새 지갑을 반환한다. */
    public TicketWallet credit(long amount) {
        requirePositive(amount);
        return new TicketWallet(id, userId, balance + amount, updatedAt);
    }

    /** 사용: 잔액이 부족하면 {@link InsufficientTicketException}. */
    public TicketWallet spend(long amount) {
        requirePositive(amount);
        if (amount > balance) {
            throw new InsufficientTicketException();
        }
        return new TicketWallet(id, userId, balance - amount, updatedAt);
    }

    /**
     * 환불 회수: 남은 잔액 한도에서만 차감한다. 이미 써버려 모자라면 0에서 멈춘다(#492) —
     * 음수 잔액으로 다음 구매분을 깎지 않는다. 못 깎은 수량은 호출 측이 구매 기록에 남긴다.
     */
    public TicketWallet revoke(long amount) {
        requirePositive(amount);
        return new TicketWallet(id, userId, Math.max(0L, balance - amount), updatedAt);
    }

    private static void requirePositive(long amount) {
        if (amount <= 0) {
            throw new InvalidTicketAmountException();
        }
    }
}
