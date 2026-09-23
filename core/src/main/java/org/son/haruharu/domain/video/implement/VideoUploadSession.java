package org.son.haruharu.domain.video.implement;
import java.time.LocalDateTime;
import java.util.UUID;
import org.son.haruharu.domain.video.implement.exception.VideoUploadUnavailableException;

/** 접수 시점의 슬롯과 게시 결과는 재시도로 바뀌지 않는다. */
public record VideoUploadSession(UUID id, Long userId, Long roomId, Long memberId,
        LocalDateTime slotStart, LocalDateTime expiresAt, String caption, Long postId, boolean isCancelled) {
    public static VideoUploadSession create(UUID id, Long userId, Long roomId, Long memberId,
            LocalDateTime slot, LocalDateTime now, String caption) {
        return new VideoUploadSession(id, userId, roomId, memberId, slot, now.plusHours(2), caption, null, false);
    }
    public void requirePending(LocalDateTime now) {
        if (isCancelled || postId != null || !now.isBefore(expiresAt)) throw new VideoUploadUnavailableException();
    }
    public VideoUploadSession completed(Long post) {
        return new VideoUploadSession(id,userId,roomId,memberId,slotStart,expiresAt,caption,post,false);
    }
    public VideoUploadSession cancelled() {
        return new VideoUploadSession(id,userId,roomId,memberId,slotStart,expiresAt,caption,postId,true);
    }
    public String publishedKey(String variant) {
        return "users/" + userId + "/videos/" + id + "/published/" + filename(variant);
    }
    public String key(String variant) {
        return "users/video-upload-staging/" + userId + "/videos/" + id + "/" + filename(variant);
    }
    private String filename(String variant) { return variant + (variant.equals("thumbnail") ? ".jpg" : ".mp4"); }
}
