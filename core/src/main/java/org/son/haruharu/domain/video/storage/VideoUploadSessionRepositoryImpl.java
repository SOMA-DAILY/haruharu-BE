package org.son.haruharu.domain.video.storage;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.son.haruharu.domain.room.implement.RoomMemberRepository;
import org.son.haruharu.domain.video.implement.*;
@Repository
@RequiredArgsConstructor
public class VideoUploadSessionRepositoryImpl implements VideoUploadSessionRepository {
    private final VideoUploadSessionJpaRepository repository;
    private final EntityManager entityManager;
    private final RoomMemberRepository members;
    public void lockMember(Long memberId) { members.lockById(memberId); }
    public Optional<VideoUploadSession> find(UUID id) { return repository.findById(id).map(VideoUploadSessionEntity::toDomain); }
    public boolean hasPending(Long memberId,LocalDateTime slot,LocalDateTime now) {
        return repository.existsByMemberIdAndSlotStartAndPostIdIsNullAndCancelledFalseAndExpiresAtAfter(memberId,slot,now);
    }
    public VideoUploadSession insert(VideoUploadSession session) {
        entityManager.persist(VideoUploadSessionEntity.from(session));
        entityManager.flush();
        return session;
    }
    public VideoUploadSession save(VideoUploadSession session) { return repository.saveAndFlush(VideoUploadSessionEntity.from(session)).toDomain(); }
}
