package org.son.haruharu.domain.room.implement;
import java.util.*;
public interface RoomParticipationRepository {
    Optional<Integer> capacity(Long roomId);
    void saveCapacity(Long roomId,int capacity);
    Optional<MemberRemoval> removal(Long memberId);
    boolean removed(Long roomId,Long userId);
    Set<String> historicalPseudonyms(Long roomId);
    List<Long> remove(MemberRemoval removal);
    RoomVisibility visibility(Long userId);
}
