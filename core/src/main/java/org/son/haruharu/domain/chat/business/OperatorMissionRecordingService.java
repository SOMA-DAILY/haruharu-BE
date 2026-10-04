package org.son.haruharu.domain.chat.business;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.chat.business.dto.OperatorMissionRecordingResponse;
import org.son.haruharu.domain.chat.business.exception.ChatNotMemberException;
import org.son.haruharu.domain.chat.implement.ChatMessageReader;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberReader;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.video.implement.SlotClock;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OperatorMissionRecordingService {
    private final ChatRoomMemberReader chatMembers;
    private final ChatMessageReader messages;
    private final RoomReader rooms;
    private final RoomMemberReader members;
    private final VideoPostReader posts;
    private final Clock clock;

    public OperatorMissionRecordingResponse get(Long chatRoomId, Long messageId, Long userId) {
        chatMembers.findMembership(chatRoomId, userId).filter(member -> !member.hasLeft())
                .orElseThrow(ChatNotMemberException::new);
        LocalDateTime now = LocalDateTime.now(clock);
        var message = messages.findById(messageId).filter(item -> item.chatRoomId().equals(chatRoomId));
        if (message.isEmpty() || message.orElseThrow().mission() == null || message.orElseThrow().adminDeleted()) {
            return new OperatorMissionRecordingResponse(false, "DELETED", null, null, null, null, now);
        }
        Long roomId = message.orElseThrow().mission().roomId();
        var room = rooms.findById(roomId).orElseThrow(RoomNotFoundException::new);
        if (room.startsAt() == null || room.statusAt(now) != RoomStatus.ACTIVE) {
            return new OperatorMissionRecordingResponse(false, "RECORDING_CLOSED", roomId, null, null, room.finalSelectionOpensAt(), now);
        }
        var member = members.getByRoomIdAndUserId(roomId, userId);
        var slot = SlotClock.currentSlotStart(room.startsAt(), now);
        boolean uploaded = posts.hasActiveUpload(member.id(), slot);
        var next = SlotClock.nextSlotStart(room.startsAt(), now);
        if (uploaded && !next.isBefore(room.finalSelectionOpensAt())) next = null;
        return new OperatorMissionRecordingResponse(!uploaded, uploaded ? "ALREADY_UPLOADED" : null, roomId, slot,
                uploaded ? next : null, room.finalSelectionOpensAt(), now);
    }
}
