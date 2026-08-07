package org.son.haruharu.domain.coin.implement;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.coin.storage.exception.CoinWalletNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CoinWalletReader {

    private final CoinWalletRepository coinWalletRepository;

    public Optional<CoinWallet> findByUserId(Long userId) {
        return coinWalletRepository.findByUserId(userId);
    }

    /** soft-delete된 지갑까지 포함한 존재 여부. 지갑 생성 전 유니크 충돌을 피하기 위한 확인이다(#298). */
    public boolean existsIncludingDeleted(Long userId) {
        return coinWalletRepository.existsIncludingDeleted(userId);
    }

    public CoinWallet getByUserId(Long userId) {
        return coinWalletRepository.findByUserId(userId)
                .orElseThrow(CoinWalletNotFoundException::new);
    }

    /**
     * 잔액을 바꾸기 위한 조회. 행 배타 락을 걸어 같은 유저의 동시 갱신을 직렬화한다(#298).
     * 지갑은 가입 시점에 생성되므로(AuthService.signup) 없으면 불변식 위반이다 — 예외로 알린다.
     */
    public CoinWallet getByUserIdForUpdate(Long userId) {
        return coinWalletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(CoinWalletNotFoundException::new);
    }
}
