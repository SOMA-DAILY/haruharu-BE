package org.son.haruharu.domain.value.implement;

import java.util.List;
import java.util.UUID;

public interface ValueAnswerUnlockRepository {

    List<ValueAnswerUnlock> findByRoomMemberId(UUID roomMemberId);

    boolean exists(UUID roomMemberId, UUID userValueAnswerId);

    ValueAnswerUnlock save(ValueAnswerUnlock unlock);
}