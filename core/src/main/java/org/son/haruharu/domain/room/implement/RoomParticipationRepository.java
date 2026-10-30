package org.son.haruharu.domain.room.implement;
import java.util.*;
public interface RoomParticipationRepository {
    Optional<MemberRemoval> removal(Long memberId);
    boolean removed(Long roomId,Long userId);
    Set<String> historicalPseudonyms(Long roomId);
    void remove(MemberRemoval removal);
    RoomVisibility visibility(Long userId);
}
