package org.son.haruharu.domain.room.implement;

import java.util.List;
import java.util.Optional;

public interface FinalSelectionRepository {

    Optional<FinalSelection> findByRoomMemberId(Long roomMemberId);

    boolean existsByRoomMemberId(Long roomMemberId);

    /** 주어진 방 멤버들의 최종선택 전체. 동시공개 resolve가 방 전체 선택을 훑을 때 쓴다. */
    List<FinalSelection> findByRoomMemberIdIn(List<Long> roomMemberIds);

    FinalSelection save(FinalSelection finalSelection);
}
