package org.son.haruharu.config;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.notification.business.DeviceTokenService;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 전역 soft-delete(#121) 메커니즘 검증.
 * BaseEntity에 @SoftDelete(TIMESTAMP)를 선언하면 delete 호출이 물리삭제 대신
 * deleted_at 세팅으로 전환되고, 일반 조회는 삭제된 행을 자동 제외해야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SoftDeleteIntegrationTest {

    @Autowired
    UserJpaRepository userJpaRepository;

    @Autowired
    EntityManager em;

    @Autowired
    DeviceTokenService deviceTokenService;

    @Test
    @DisplayName("BaseEntity 상속 엔티티를 delete하면 물리삭제 대신 deleted_at이 세팅된다")
    void givenBaseEntity_whenDelete_thenSoftDeletedRowRemainsWithTimestamp() {
        // given
        Long id = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null)))
                .toDomain().id();
        em.flush();

        // when
        userJpaRepository.deleteById(id);
        em.flush();
        em.clear();

        // then — 물리 행은 남아있고 deleted_at이 세팅된다 (native로 필터 우회 확인)
        Number remaining = (Number) em.createNativeQuery(
                        "select count(*) from users where id = :id and deleted_at is not null")
                .setParameter("id", id)
                .getSingleResult();
        assertThat(remaining.longValue()).isEqualTo(1L);
    }

    @Test
    @DisplayName("soft-delete된 엔티티는 일반 조회(findById)에서 자동 제외된다")
    void givenSoftDeleted_whenFindById_thenExcludedByGlobalFilter() {
        // given
        Long id = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null)))
                .toDomain().id();
        em.flush();
        userJpaRepository.deleteById(id);
        em.flush();
        em.clear();

        // when & then — 전역 필터로 조회 제외
        assertThat(userJpaRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("DeviceToken은 soft-delete 예외 — unregister 후 같은 토큰 재등록이 unique 충돌 없이 성공한다")
    void givenUnregisteredToken_whenReregisterSameToken_thenPhysicalDeleteAvoidsConflict() {
        // given
        Long userId = 1L;
        String token = "fcm-token-reregister";
        deviceTokenService.register(userId, token);
        deviceTokenService.unregister(userId, token);

        // when — 재등록. DeviceToken은 물리삭제라 죽은 행이 남지 않아 충돌하지 않는다.
        deviceTokenService.register(userId, token);
        em.flush();

        // then — 물리 행이 정확히 1개 (soft-deleted 잔존 없음)
        Number count = (Number) em.createNativeQuery(
                        "select count(*) from device_tokens where token = :token")
                .setParameter("token", token)
                .getSingleResult();
        assertThat(count.longValue()).isEqualTo(1L);
    }
}
