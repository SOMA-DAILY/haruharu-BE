package org.son.haruharu.domain.video.storage;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.video.implement.VideoUploadSession;
import org.son.haruharu.domain.video.implement.VideoUploadSessionRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class VideoUploadSessionRepositoryImpl implements VideoUploadSessionRepository {

    private final VideoUploadSessionJpaRepository jpaRepository;
    private final EntityManager entityManager;

    @Override
    public Optional<VideoUploadSession> findById(UUID id) {
        return jpaRepository.findById(id).map(VideoUploadSessionEntity::toDomain);
    }

    @Override
    public List<VideoUploadSession> findPending(Long memberId, LocalDateTime slotStart, LocalDateTime now) {
        return jpaRepository.findByMemberIdAndSlotStartAndPostIdIsNullAndCancelledFalseAndExpiresAtAfter(
                        memberId, slotStart, now).stream()
                .map(VideoUploadSessionEntity::toDomain)
                .toList();
    }

    @Override
    public List<VideoUploadSession> findPendingByMember(Long memberId, LocalDateTime now) {
        return jpaRepository.findByMemberIdAndPostIdIsNullAndCancelledFalseAndExpiresAtAfterOrderBySlotStartAsc(
                        memberId, now).stream()
                .map(VideoUploadSessionEntity::toDomain)
                .toList();
    }

    @Override
    public VideoUploadSession insert(VideoUploadSession session) {
        // 할당 id라 save()는 merge로 기존 행을 덮어쓸 수 있다. persist로 PK 충돌을 드러낸다.
        entityManager.persist(VideoUploadSessionEntity.from(session));
        entityManager.flush();
        return session;
    }

    @Override
    public VideoUploadSession save(VideoUploadSession session) {
        return jpaRepository.saveAndFlush(VideoUploadSessionEntity.from(session)).toDomain();
    }
}
