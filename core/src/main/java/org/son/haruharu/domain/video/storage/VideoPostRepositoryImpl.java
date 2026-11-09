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
    public boolean updateCaption(Long videoPostId, String caption) {
        return jpaRepository.updateCaption(videoPostId, caption) == 1;
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
    public List<LocalDateTime> findVisibleSlotStarts(List<Long> roomMemberIds, List<LocalDateTime> slotStarts) {
        if (roomMemberIds.isEmpty() || slotStarts.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findVisibleSlotStarts(roomMemberIds, slotStarts);
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

    @Override
    public List<Long> findUploadedRoomMemberIds(List<Long> roomMemberIds, List<LocalDateTime> slotStarts) {
        if (roomMemberIds.isEmpty() || slotStarts.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findUploadedRoomMemberIds(roomMemberIds, slotStarts);
    }

    @Override
    public java.util.Set<Long> findUnavailableIds(java.util.Collection<Long> ids) {
        if (ids.isEmpty()) {
            return java.util.Set.of();
        }
        // @SQLRestriction 때문에 퇴장 멤버의 영상은 조회되지 않는다(없음 = 볼 수 없음).
        java.util.Set<Long> visible = new java.util.HashSet<>();
        jpaRepository.findAllById(ids).forEach(entity -> {
            VideoPost post = entity.toDomain();
            if (post.deletedAt() == null && !post.isHidden()) {
                visible.add(post.id());
            }
        });
        java.util.Set<Long> unavailable = new java.util.HashSet<>(ids);
        unavailable.removeAll(visible);
        return unavailable;
    }
}
