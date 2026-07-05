package org.son.haruharu.domain.notification.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.notification.implement.DeviceToken;
import org.son.haruharu.domain.notification.implement.DeviceTokenRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DeviceTokenRepositoryImpl implements DeviceTokenRepository {

    private final DeviceTokenJpaRepository jpaRepository;

    @Override
    public DeviceToken save(DeviceToken deviceToken) {
        return jpaRepository.save(DeviceTokenEntity.from(deviceToken)).toDomain();
    }

    @Override
    public Optional<DeviceToken> findByToken(String token) {
        return jpaRepository.findByToken(token).map(DeviceTokenEntity::toDomain);
    }

    @Override
    public List<DeviceToken> findAllByUserIdIn(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findAllByUserIdIn(userIds).stream()
                .map(DeviceTokenEntity::toDomain)
                .toList();
    }

    // 파생 delete 쿼리는 트랜잭션이 필요하다. 발송 후 무효 토큰 정리는 Service 트랜잭션 밖에서
    // 호출되므로(발송 IO를 트랜잭션에 넣지 않기 위해) 어댑터가 자체 경계를 연다.
    @Override
    @Transactional
    public void deleteByUserIdAndToken(Long userId, String token) {
        jpaRepository.deleteByUserIdAndToken(userId, token);
    }

    @Override
    @Transactional
    public void deleteAllByTokens(Collection<String> tokens) {
        jpaRepository.deleteByTokenIn(tokens);
    }
}
