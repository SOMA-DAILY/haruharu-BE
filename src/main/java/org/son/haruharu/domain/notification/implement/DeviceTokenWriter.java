package org.son.haruharu.domain.notification.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
@RequiredArgsConstructor
public class DeviceTokenWriter {

    private final DeviceTokenRepository deviceTokenRepository;

    public DeviceToken upsert(Long userId, String token) {
        return deviceTokenRepository.findByToken(token)
                .map(existing -> existing.userId().equals(userId)
                        ? existing
                        : deviceTokenRepository.save(existing.reassignedTo(userId)))
                .orElseGet(() -> deviceTokenRepository.save(DeviceToken.of(userId, token)));
    }

    public void delete(Long userId, String token) {
        deviceTokenRepository.deleteByUserIdAndToken(userId, token);
    }

    public void deleteAllByTokens(Collection<String> tokens) {
        if (tokens.isEmpty()) {
            return;
        }
        deviceTokenRepository.deleteAllByTokens(tokens);
    }
}
