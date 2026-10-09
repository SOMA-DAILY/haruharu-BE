package org.son.haruharu.domain.video.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface VideoUploadSessionJpaRepository extends JpaRepository<VideoUploadSessionEntity, UUID> {

    List<VideoUploadSessionEntity> findByMemberIdAndSlotStartAndPostIdIsNullAndCancelledFalseAndExpiresAtAfter(
            Long memberId, LocalDateTime slotStart, LocalDateTime now);

    List<VideoUploadSessionEntity> findByMemberIdAndPostIdIsNullAndCancelledFalseAndExpiresAtAfterOrderBySlotStartAsc(
            Long memberId, LocalDateTime now);
}
