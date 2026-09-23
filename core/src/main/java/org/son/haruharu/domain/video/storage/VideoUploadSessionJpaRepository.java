package org.son.haruharu.domain.video.storage;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface VideoUploadSessionJpaRepository extends JpaRepository<VideoUploadSessionEntity,UUID> {
    boolean existsByMemberIdAndSlotStartAndPostIdIsNullAndCancelledFalseAndExpiresAtAfter(Long memberId,LocalDateTime slot,LocalDateTime now);
}
