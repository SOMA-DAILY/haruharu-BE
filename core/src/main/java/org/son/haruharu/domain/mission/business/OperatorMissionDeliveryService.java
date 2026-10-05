package org.son.haruharu.domain.mission.business;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.chat.business.OperatorMissionChatService;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.mission.implement.*;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OperatorMissionDeliveryService {
    private final OperatorMissionReader reader;
    private final OperatorMissionWriter writer;
    private final RoomReader roomReader;
    private final OperatorMissionChatService chat;
    private final Clock clock;

    public List<Long> dueIds(int limit) { return reader.dueIds(LocalDateTime.now(clock), limit); }
    public List<Long> pendingDeleteIds(int limit) { return reader.pendingDeleteIds(limit); }

    /** 예약 행을 잠근 다음 최신 시각·상태를 다시 판정한다. 카드와 결과는 함께 커밋된다. */
    @Transactional
    public void deliver(Long id) {
        OperatorMission mission = reader.getForUpdate(id);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!mission.dueAt(now)) return;
        var room = roomReader.findByIdForUpdate(mission.roomId()).orElse(null);
        now = LocalDateTime.now(clock);
        if (room == null || room.statusAt(now) != RoomStatus.ACTIVE) {
            writer.save(mission.failed("방의 촬영 가능 기간이 끝났거나 방이 취소됐어요."));
            return;
        }
        ChatMessage message = chat.send(mission);
        writer.save(mission.sent(message.id(), LocalDateTime.now(clock)));
    }

    @Transactional
    public void fail(Long id) {
        OperatorMission mission = reader.getForUpdate(id);
        if (mission.dueAt(LocalDateTime.now(clock))) {
            writer.save(mission.failed("미션을 보내지 못했어요. 운영 설정을 확인하고 다시 보내 주세요."));
        }
    }

    @Transactional
    public void delete(Long id) {
        OperatorMission mission = reader.getForUpdate(id);
        if (mission.deleteRequestedAt() == null || mission.deleteProcessedAt() != null) return;
        chat.delete(mission.messageId(), mission.id());
        writer.save(mission.deleteProcessed(LocalDateTime.now(clock)));
    }
}
