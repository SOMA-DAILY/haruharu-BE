package org.son.haruharu.domain.room.implement;

import java.util.Optional;

public interface FinalSelectionRepository {

    Optional<FinalSelection> findByRoomMemberId(Long roomMemberId);

    boolean existsByRoomMemberId(Long roomMemberId);

    FinalSelection save(FinalSelection finalSelection);
}
