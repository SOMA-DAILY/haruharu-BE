package org.son.haruharu.domain.video.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class VideoPostRepositoryImpl implements VideoPostRepository {

    private final VideoPostJpaRepository jpaRepository;

    @Override
    public VideoPost save(VideoPost post) {
        return jpaRepository.save(VideoPostEntity.from(post)).toDomain();
    }

    @Override
    public Optional<VideoPost> findById(Long id) {
        return jpaRepository.findById(id).map(VideoPostEntity::toDomain);
    }

    @Override
    public List<LocalDateTime> findUploadedSlotStarts(Long roomMemberId, List<LocalDateTime> slotStarts) {
        if (slotStarts.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findUploadedSlotStarts(roomMemberId, slotStarts);
    }

    @Override
    public boolean existsActiveBySlotStart(Long roomMemberId, LocalDateTime slotStart) {
        return jpaRepository.existsByRoomMemberIdAndSlotStartAndDeletedAtIsNull(roomMemberId, slotStart);
    }

    @Override
    public List<VideoPost> findFeed(List<Long> roomMemberIds, List<LocalDateTime> slotStarts) {
        if (roomMemberIds.isEmpty() || slotStarts.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByRoomMemberIdInAndSlotStartInAndDeletedAtIsNullAndIsHiddenFalse(
                        roomMemberIds, slotStarts).stream()
                .map(VideoPostEntity::toDomain)
                .toList();
    }

    @Override
    public List<VideoPost> findActiveByRoomMember(Long roomMemberId) {
        return jpaRepository.findByRoomMemberIdAndDeletedAtIsNullAndIsHiddenFalseOrderBySlotStartAsc(
                        roomMemberId).stream()
                .map(VideoPostEntity::toDomain)
                .toList();
    }
}
