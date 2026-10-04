package org.son.haruharu.domain.video.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface VideoUploadSessionJpaRepository extends JpaRepository<VideoUploadSessionEntity, UUID> {

    boolean existsByMemberIdAndSlotStartAndPostIdIsNullAndCancelledFalseAndExpiresAtAfter(
            Long memberId, LocalDateTime slotStart, LocalDateTime now);
}
