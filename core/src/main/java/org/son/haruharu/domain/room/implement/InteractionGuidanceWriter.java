package org.son.haruharu.domain.room.implement;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.config.InteractionGuidanceProperties;
import org.springframework.stereotype.Component;

/** 반응·답장 성공 트랜잭션에서 호출한다. OFF/등록 대상 밖에서는 신규 테이블에 접근하지 않는다. */
@Component
@RequiredArgsConstructor
public class InteractionGuidanceWriter {
    private final InteractionGuidanceProperties properties;
    private final RoomReader rooms;
    private final RoomMemberReader members;
    private final InteractionGuidanceRepository repository;
    private final Clock clock;

    public void recordFirstReaction(RoomMember member) {
        record(member, InteractionGuideKind.REACTION);
    }

    public void recordFirstVideoReply(RoomMember member) {
        record(member, InteractionGuideKind.REPLY);
    }

    private void record(RoomMember member, InteractionGuideKind kind) {
        if (!properties.enabled()) return;
        var room = rooms.findById(member.roomId());
        if (room.isEmpty() || !properties.enrolls(room.get())) return;
        members.lockById(member.id());
        var actions = repository.findFirstActions(member.id()).orElse(InteractionFirstActions.empty(member.id()));
        repository.saveFirstActions(actions.record(kind, LocalDateTime.now(clock)));
    }
}
