package org.son.haruharu.domain.mission.chat.business;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.chat.business.dto.ChatMessageBroadcast;
import org.son.haruharu.domain.chat.business.event.ChatMessageBroadcastEvent;
import org.son.haruharu.domain.chat.implement.*;
import org.son.haruharu.domain.mission.chat.business.exception.MissionDispatchRejectedException;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncement;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementNotifier;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementReader;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementStatus;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementWriter;
import org.son.haruharu.domain.mission.chat.implement.MissionChatLookup;
import org.son.haruharu.domain.mission.chat.implement.MissionNotification;
import org.son.haruharu.domain.mission.common.implement.RoomMission;
import org.son.haruharu.domain.mission.common.implement.RoomMissionReader;
import org.son.haruharu.domain.room.implement.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@org.springframework.transaction.annotation.Transactional(readOnly = true)
@RequiredArgsConstructor
public class MissionDispatchStep {
    private final RoomReader rooms;
    private final RoomMissionReader missions;
    private final List<MissionAnnouncementNotifier> notifiers;
    private final MissionChatLookup chatLookup;
    private final MissionAnnouncementReader announcements;
    private final MissionAnnouncementWriter writer;
    private final ChatRoomReader chatRooms;
    private final ChatMessageWriter messages;
    private final ChatRoomMemberReader members;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(Long id, LocalDateTime now) { return writer.claim(id, now); }

    /** 원본을 다시 잠가 확인한 뒤 메시지 저장과 SENT를 한 번에 커밋한다. 방송 리스너는 AFTER_COMMIT이다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SendResult send(MissionAnnouncement snapshot, Long chatRoomId) {
        Room room = rooms.findByIdForUpdate(snapshot.roomId())
                .orElseThrow(() -> new MissionDispatchRejectedException("ROOM_NOT_FOUND"));
        LocalDateTime now = LocalDateTime.now(clock);
        if (room.statusAt(now) == RoomStatus.ENDED || room.statusAt(now) == RoomStatus.CANCELLED)
            throw new MissionDispatchRejectedException("ROOM_ENDED");
        if (room.statusAt(now) == RoomStatus.PENDING) throw new MissionDispatchRejectedException("ROOM_NOT_STARTED");
        RoomMission mission = missions.getForUpdate(snapshot.roomMissionId());
        MissionAnnouncement item = announcements.getForUpdate(snapshot.id());
        if (!mission.roomId().equals(room.id()) || !item.roomMissionId().equals(mission.id())
                || !item.roomId().equals(room.id())) throw new MissionDispatchRejectedException("MISSION_ROOM_MISMATCH");
        if (mission.canceledAt() != null) throw new MissionDispatchRejectedException("MISSION_CANCELED");
        if (now.isBefore(mission.opensAt())) throw new MissionDispatchRejectedException("MISSION_NOT_STARTED");
        if (item.status() != MissionAnnouncementStatus.SENDING || item.runAt().isAfter(now))
            throw new MissionDispatchRejectedException("ANNOUNCEMENT_NOT_SENDING");
        chatRooms.lockForUpdate(chatRoomId);
        ChatRoom chatRoom = chatRooms.getById(chatRoomId);
        if (chatRoom.type() != ChatRoomType.GROUP || !room.id().equals(chatRoom.roomId()))
            throw new MissionDispatchRejectedException("MISSION_CHAT_ROOM_MISMATCH");
        if (chatRoom.closeAt() != null) throw new MissionDispatchRejectedException("ROOM_ENDED");
        Long messageId = null;
        if (item.hasMessage()) {
            ChatMessage saved = messages.save(ChatMessage.mission(chatRoomId, item.content(), mission.id(), item.id()));
            messageId = saved.id();
            var reference = new ChatMissionReference(mission.id(), item.id(), item.announcementType().name(),
                    mission.type().name(), chatLookup.image(mission));
            publisher.publishEvent(new ChatMessageBroadcastEvent(ChatMessageBroadcast.from(saved, reference)));
        }
        if (!writer.markSent(item.id(), messageId, now)) throw new MissionDispatchRejectedException("ANNOUNCEMENT_NOT_SENDING");
        List<Long> recipients = members.findByChatRoomId(chatRoomId).stream().filter(m -> !m.hasLeft())
                .map(ChatRoomMember::userId).toList();
        List<MissionNotification> notifications = notifiers.stream().filter(n -> n.type() == mission.type())
                .flatMap(n -> n.afterAnnouncement(mission, item).stream()).toList();
        return new SendResult(item, recipients, notifications);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean fail(Long id, MissionAnnouncementStatus from, String reason, LocalDateTime now) {
        return writer.markFailed(id, from, reason, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean failIfUnchanged(MissionAnnouncement snapshot, String reason, LocalDateTime now) {
        return writer.markFailedIfUnchanged(snapshot, reason, now);
    }

    public record SendResult(MissionAnnouncement announcement, List<Long> recipients, List<MissionNotification> notifications) {}
}
