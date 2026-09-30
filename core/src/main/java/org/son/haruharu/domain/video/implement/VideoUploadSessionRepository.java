package org.son.haruharu.domain.video.implement;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface VideoUploadSessionRepository {

    Optional<VideoUploadSession> findById(UUID id);

    /** 같은 멤버·슬롯에 만료·취소·게시되지 않은 세션이 있는지. */
    boolean existsPending(Long memberId, LocalDateTime slotStart, LocalDateTime now);

    /** 새 세션 INSERT. 같은 id가 이미 있으면 덮어쓰지 않고 제약 위반으로 실패한다. */
    VideoUploadSession insert(VideoUploadSession session);

    VideoUploadSession save(VideoUploadSession session);
}
