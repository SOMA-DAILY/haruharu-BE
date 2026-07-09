package org.son.haruharu.domain.notification.implement;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository {

    DeviceToken save(DeviceToken deviceToken);

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findAllByUserIdIn(Collection<Long> userIds);

    void deleteByUserIdAndToken(Long userId, String token);

    void deleteAllByTokens(Collection<String> tokens);
}
