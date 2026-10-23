package org.son.haruharu.domain.chat.implement;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.chat.business.dto.ChatPhotoView;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ChatPhotoStore {
    private final S3StoragePort storage;

    public String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public ChatPhoto upload(Long roomId, byte[] bytes, ChatPhotoFile file) {
        String key = "chat-photos/" + roomId + "/" + UUID.randomUUID() + "." + file.extension();
        // DB 저장/커밋 실패 때 사진이 고아 객체로 남지 않게 한다.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) storage.deleteObjects(List.of(key));
                }
            });
        }
        storage.putImage(key, file.contentType(), bytes);
        return new ChatPhoto(key, file.width(), file.height(), digest(bytes));
    }

    public ChatPhotoView view(ChatPhoto photo) {
        return photo == null ? null : new ChatPhotoView(photo.key(),
                storage.createPresignedImageGetUrl(storage.publicBaseUrl() + photo.key()), photo.width(), photo.height());
    }
}
