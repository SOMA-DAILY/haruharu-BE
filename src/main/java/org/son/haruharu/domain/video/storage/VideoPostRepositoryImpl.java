package org.son.haruharu.domain.video.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostRepository;
import org.springframework.stereotype.Repository;

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
    public long countUploaded(Long roomMemberId, int dayNumber) {
        return jpaRepository.countByRoomMemberIdAndDayNumberAndDeletedAtIsNull(roomMemberId, dayNumber);
    }

    @Override
    public boolean existsActiveBySlot(Long roomMemberId, int dayNumber, int slotIndex) {
        return jpaRepository.existsByRoomMemberIdAndDayNumberAndSlotIndexAndDeletedAtIsNull(
                roomMemberId, dayNumber, slotIndex);
    }

    @Override
    public boolean existsMissionUploadBySlot(Long roomMemberId, int dayNumber, int slotIndex) {
        return jpaRepository.existsByRoomMemberIdAndDayNumberAndSlotIndexAndMissionIdIsNotNull(
                roomMemberId, dayNumber, slotIndex);
    }

    @Override
    public List<VideoPost> findFeed(List<Long> roomMemberIds, int dayNumber) {
        if (roomMemberIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByRoomMemberIdInAndDayNumberAndDeletedAtIsNullAndIsHiddenFalse(
                        roomMemberIds, dayNumber).stream()
                .map(VideoPostEntity::toDomain)
                .toList();
    }
}
