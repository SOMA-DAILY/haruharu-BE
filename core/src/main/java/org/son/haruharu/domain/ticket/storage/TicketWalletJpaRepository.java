package org.son.haruharu.domain.ticket.storage;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TicketWalletJpaRepository extends JpaRepository<TicketWalletEntity, Long> {

    Optional<TicketWalletEntity> findByUserId(Long userId);

    /**
     * 잔액 변경(적립·소모·회수) 전용 조회. 행 단위 배타 락(SELECT ... FOR UPDATE)을 걸어
     * 같은 유저에 대한 동시 read-modify-write가 서로의 갱신을 덮어쓰지 못하게 한다(#298).
     * 조회 API에는 쓰지 않는다 — 읽기까지 직렬화할 이유가 없다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from TicketWalletEntity w where w.userId = :userId")
    Optional<TicketWalletEntity> findByUserIdForUpdate(@Param("userId") Long userId);

    /**
     * soft-delete된 행까지 포함한 지갑 개수. 지갑 생성 전 존재 확인에 쓴다(#298).
     * <p>
     * user_id 유니크 제약은 deleted_at과 무관하게 걸리는데 @SoftDelete가 붙은 일반 조회는 죽은 행을
     * 보지 못한다. 그래서 탈퇴로 soft-delete된 지갑이 남은 계정에 일반 조회로 판단해 INSERT하면
     * 제약 위반이 난다. 죽은 행이 유니크 슬롯을 점유하는 이 구조는 user_credentials(#147)와 동일하다.
     */
    @Query(value = "SELECT count(*) FROM ticket_wallet WHERE user_id = :userId", nativeQuery = true)
    long countByUserIdIncludingDeleted(@Param("userId") Long userId);

    void deleteByUserId(Long userId);
}
