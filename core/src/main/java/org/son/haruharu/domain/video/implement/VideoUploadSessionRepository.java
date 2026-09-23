package org.son.haruharu.domain.video.implement;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
public interface VideoUploadSessionRepository {
    /** 회원 행 잠금으로 서로 다른 작업의 동시 접수도 직렬화한다. */
    void lockMember(Long memberId);
    Optional<VideoUploadSession> find(UUID id);
    boolean hasPending(Long memberId, LocalDateTime slot, LocalDateTime now);
    VideoUploadSession insert(VideoUploadSession session);
    VideoUploadSession save(VideoUploadSession session);
}
