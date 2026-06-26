package org.son.haruharu.domain.video.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.video.implement.VideoPostRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class VideoPostRepositoryImpl implements VideoPostRepository {

    private final VideoPostJpaRepository jpaRepository;

    @Override
    public long countUploaded(UUID roomMemberId, List<UUID> slotIds) {
        return jpaRepository.countByRoomMemberIdAndUploadSlotIdInAndDeletedAtIsNull(roomMemberId, slotIds);
    }
}
