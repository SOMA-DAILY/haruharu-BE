package org.son.haruharu.domain.room.implement;

import java.util.Optional;
import java.util.UUID;

public interface FinalSelectionRepository {

    Optional<FinalSelection> findByRoomMemberId(UUID roomMemberId);

    boolean existsByRoomMemberId(UUID roomMemberId);

    FinalSelection save(FinalSelection finalSelection);
}
