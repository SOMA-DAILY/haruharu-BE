package org.son.haruharu.domain.video.implement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VideoUploadSessionRepository {

    Optional<VideoUploadSession> findById(UUID id);

    /** 같은 멤버·슬롯에서 만료·취소·게시되지 않은 세션. */
    List<VideoUploadSession> findPending(Long memberId, LocalDateTime slotStart, LocalDateTime now);

    /** 한 멤버의 만료·취소·게시되지 않은 세션 전체(slot_start 오름차순). */
    List<VideoUploadSession> findPendingByMember(Long memberId, LocalDateTime now);

    /** 새 세션 INSERT. 같은 id가 이미 있으면 덮어쓰지 않고 제약 위반으로 실패한다. */
    VideoUploadSession insert(VideoUploadSession session);

    VideoUploadSession save(VideoUploadSession session);
}
