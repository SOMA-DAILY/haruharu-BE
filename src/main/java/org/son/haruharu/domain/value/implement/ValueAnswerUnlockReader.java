package org.son.haruharu.domain.value.implement;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ValueAnswerUnlockReader {

    private final ValueAnswerUnlockRepository valueAnswerUnlockRepository;

    public ValueAnswerUnlockReader(ValueAnswerUnlockRepository valueAnswerUnlockRepository) {
        this.valueAnswerUnlockRepository = valueAnswerUnlockRepository;
    }

    /** roomMember가 해금한 상대 답변 id 집합. 프로필 조회 시 답변별 해금 여부 판정에 쓴다. */
    public List<UUID> getUnlockedAnswerIds(UUID roomMemberId) {
        return valueAnswerUnlockRepository.findByRoomMemberId(roomMemberId).stream()
                .map(ValueAnswerUnlock::userValueAnswerId)
                .toList();
    }

    public boolean isUnlocked(UUID roomMemberId, UUID userValueAnswerId) {
        return valueAnswerUnlockRepository.exists(roomMemberId, userValueAnswerId);
    }
}