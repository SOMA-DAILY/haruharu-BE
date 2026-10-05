package org.son.haruharu.domain.mission.implement;

import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@Slf4j
public class MissionImageUploader {
    private final S3StoragePort storage;
    private final long systemUserId;
    // haruharu.chat.system-user-id 는 app 에서 빈 값일 수 있어(#455 yaml) 빈 문자열은 0 으로 받는다.
    public MissionImageUploader(S3StoragePort storage,
                                @org.springframework.beans.factory.annotation.Value(
                                        "#{'${haruharu.chat.system-user-id:}'.isBlank() ? 0L : T(java.lang.Long).parseLong('${haruharu.chat.system-user-id:}')}")
                                long systemUserId) {
        this.storage = storage;
        this.systemUserId = systemUserId;
    }

    public MissionImage upload(Long roomId, Long adminId, byte[] bytes, String contentType) {
        var file = MissionImageFile.validate(bytes, contentType);
        if (systemUserId <= 0) throw new IllegalStateException("운영자 미션의 시스템 유저 설정이 필요합니다.");
        String key = "users/" + systemUserId + "/mission-images/" + roomId + "/" + UUID.randomUUID() + "." + file.extension();
        storage.putImage(key, file.contentType(), bytes);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        try { storage.deleteObjects(List.of(key)); }
                        catch (RuntimeException exception) { log.warn("롤백한 미션 사진 정리에 실패했습니다."); }
                    }
                }
            });
        }
        return new MissionImage(null, roomId, adminId, key, file.width(), file.height());
    }
}
