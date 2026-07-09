package org.son.haruharu.domain.notification.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DeviceTokenReader {

    private final DeviceTokenRepository deviceTokenRepository;

    public List<DeviceToken> findAllByUserIds(Collection<Long> userIds) {
        return deviceTokenRepository.findAllByUserIdIn(userIds);
    }
}
