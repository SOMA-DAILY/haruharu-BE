package org.son.haruharu.domain.notification.business;

import org.son.haruharu.domain.notification.implement.DeviceTokenWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DeviceTokenService {

    private final DeviceTokenWriter deviceTokenWriter;

    public DeviceTokenService(DeviceTokenWriter deviceTokenWriter) {
        this.deviceTokenWriter = deviceTokenWriter;
    }

    @Transactional
    public void register(Long userId, String token) {
        deviceTokenWriter.upsert(userId, token);
    }

    @Transactional
    public void unregister(Long userId, String token) {
        deviceTokenWriter.delete(userId, token);
    }
}
