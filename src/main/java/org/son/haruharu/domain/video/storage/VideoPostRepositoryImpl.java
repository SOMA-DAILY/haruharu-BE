package org.son.haruharu.domain.video.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class VideoPostRepositoryImpl implements VideoPostRepository {

    private final VideoPostJpaRepository jpaRepository;

    @Override
    public VideoPost save(VideoPost post) {
        return jpaRepository.save(VideoPostEntity.from(post)).toDomain();
    }

    @Override
    public Optional<VideoPost> findById(UUID id) {
        return jpaRepository.findById(id).map(VideoPostEntity::toDomain);
    }

    @Override
    public long countUploaded(UUID roomMemberId, List<UUID> slotIds) {
        return jpaRepository.countByRoomMemberIdAndUploadSlotIdInAndDeletedAtIsNull(roomMemberId, slotIds);
    }

    @Override
    public boolean existsActiveBySlot(UUID roomMemberId, UUID uploadSlotId) {
        return jpaRepository.existsByRoomMemberIdAndUploadSlotIdAndDeletedAtIsNull(roomMemberId, uploadSlotId);
    }

    @Override
    public boolean existsMissionUploadBySlot(UUID roomMemberId, UUID uploadSlotId) {
        return jpaRepository.existsByRoomMemberIdAndUploadSlotIdAndMissionIdIsNotNull(roomMemberId, uploadSlotId);
    }

    @Override
    public List<VideoPost> findFeed(List<UUID> slotIds) {
        if (slotIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByUploadSlotIdInAndDeletedAtIsNullAndIsHiddenFalse(slotIds).stream()
                .map(VideoPostEntity::toDomain)
                .toList();
    }
}
