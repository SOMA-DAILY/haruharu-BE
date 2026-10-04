package org.son.haruharu.domain.chat.business;

import org.son.haruharu.domain.chat.business.dto.ChatMessageBroadcast;
import org.son.haruharu.domain.chat.business.event.ChatMessageBroadcastEvent;
import org.son.haruharu.domain.chat.business.event.MessageDeletedEvent;
import org.son.haruharu.domain.chat.business.event.OperatorMissionMessageSentEvent;
import org.son.haruharu.domain.chat.implement.*;
import org.son.haruharu.domain.mission.implement.OperatorMission;
import org.son.haruharu.domain.mission.implement.OperatorMissionTemplate;
import org.son.haruharu.domain.user.business.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.mission.implement.exception.InvalidOperatorMissionScheduleException;

@Service
@Transactional(readOnly = true)
public class OperatorMissionChatService {
    private final ChatService chatService;
    private final ChatRoomReader roomReader;
    private final ChatRoomMemberReader memberReader;
    private final ChatMessageReader messageReader;
    private final ChatMessageWriter writer;
    private final UserService userService;
    private final ApplicationEventPublisher publisher;
    private final long systemUserId;
    private final RoomReader sourceRoomReader;
    private final Clock clock;

    public OperatorMissionChatService(ChatService chatService, ChatRoomReader roomReader, ChatRoomMemberReader memberReader,
                                      ChatMessageReader messageReader, ChatMessageWriter writer, UserService userService,
                                      ApplicationEventPublisher publisher,
                                      @Value("${haruharu.chat.system-user-id:0}") long systemUserId, RoomReader sourceRoomReader, Clock clock) {
        this.chatService = chatService; this.roomReader = roomReader; this.memberReader = memberReader;
        this.messageReader = messageReader; this.writer = writer; this.userService = userService;
        this.publisher = publisher; this.systemUserId = systemUserId;
        this.sourceRoomReader = sourceRoomReader; this.clock = clock;
    }

    @Transactional
    public ChatMessage send(OperatorMission mission) {
        if (systemUserId <= 0 || userService.isWithdrawn(systemUserId)) {
            throw new IllegalStateException("운영자 미션의 시스템 유저 설정이 필요합니다.");
        }
        chatService.ensureGroupRoom(mission.roomId());
        ChatRoom room = roomReader.getByRoomId(mission.roomId());
        roomReader.lockForUpdate(room.id());
        var sourceRoom = sourceRoomReader.findById(mission.roomId()).orElseThrow(RoomNotFoundException::new);
        if (sourceRoom.statusAt(LocalDateTime.now(clock)) != RoomStatus.ACTIVE) {
            throw new InvalidOperatorMissionScheduleException();
        }
        ChatMission card = new ChatMission(mission.id(), mission.roomId(), mission.templateId(),
                OperatorMissionTemplate.get(mission.templateId()).title(), mission.imageId(), null);
        ChatMessage saved = writer.save(ChatMessage.mission(room.id(), systemUserId, mission.content(), card));
        publisher.publishEvent(new ChatMessageBroadcastEvent(ChatMessageBroadcast.from(saved)));
        if (mission.notifyParticipants()) {
            var recipients = memberReader.findByChatRoomId(room.id()).stream().filter(m -> !m.hasLeft())
                    .map(ChatRoomMember::userId).filter(id -> !userService.isWithdrawn(id)).toList();
            publisher.publishEvent(new OperatorMissionMessageSentEvent(mission.id(), room.id(), recipients));
        }
        return saved;
    }

    @Transactional
    public void delete(Long messageId, Long missionId) {
        messageReader.findById(messageId).ifPresent(message -> {
            if (message.mission() == null || !message.mission().missionId().equals(missionId)) return;
            roomReader.lockForUpdate(message.chatRoomId());
            if (writer.markAdminDeleted(messageId)) {
                publisher.publishEvent(new MessageDeletedEvent(message.chatRoomId(), messageId));
            }
        });
    }
}
