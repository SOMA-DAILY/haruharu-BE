package org.son.haruharu.domain.mission.chat.business;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatMessageReader;
import org.son.haruharu.domain.chat.implement.ChatMessageWriter;
import org.son.haruharu.domain.mission.chat.business.command.MissionAnnouncementCommand;
import org.son.haruharu.domain.mission.chat.business.dto.MissionAnnouncementResponse;
import org.son.haruharu.domain.mission.chat.business.exception.AnnouncementNotEditableException;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncement;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementContent;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementReader;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementStatus;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementWriter;
import org.son.haruharu.domain.mission.common.implement.MissionDefinition;
import org.son.haruharu.domain.mission.common.implement.MissionDetailsReader;
import org.son.haruharu.domain.mission.common.implement.MissionTypeRegistry;
import org.son.haruharu.domain.mission.common.implement.RoomMission;
import org.son.haruharu.domain.mission.common.implement.RoomMissionReader;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissionAnnouncementService {
    private final RoomReader rooms;
    private final RoomMissionReader missions;
    private final MissionAnnouncementReader reader;
    private final MissionAnnouncementWriter writer;
    private final ChatMessageReader messages;
    private final ChatMessageWriter messageWriter;
    private final MissionTypeRegistry types;
    private final MissionDetailsReader detailsReader;
    private final Clock clock;

    @Transactional
    public MissionAnnouncementResponse add(Long roomId, Long missionId, MissionAnnouncementCommand command, String actor) {
        RoomMission mission = lockMission(roomId, missionId, true);
        validate(mission, detailsReader.get(mission), command);
        return response(writer.save(MissionAnnouncement.scheduled(mission, command.announcementType(), command.runAt(),
                MissionAnnouncementContent.text(command.content()), Boolean.TRUE.equals(command.push()),
                MissionAnnouncementContent.text(command.pushTitle()), MissionAnnouncementContent.text(command.pushBody()), actor)));
    }

    public void validate(RoomMission mission, MissionDefinition details, MissionAnnouncementCommand command) {
        MissionAnnouncementContent.validateAnnouncement(mission, command.announcementType(), command.runAt(),
                command.content(), command.pushTitle(), command.pushBody(),
                types.get(mission.type()).allowsEmptyAnnouncement(command.announcementType(), details));
    }

    @Transactional
    public MissionAnnouncementResponse update(Long roomId, Long missionId, Long id,
                                               MissionAnnouncementCommand command, String actor) {
        RoomMission mission = lockMission(roomId, missionId, true);
        MissionAnnouncement current = lockAnnouncement(mission, id);
        if (current.status() != MissionAnnouncementStatus.SCHEDULED || current.announcementType() != command.announcementType())
            throw new AnnouncementNotEditableException();
        validate(mission, detailsReader.get(mission), command);
        if (!writer.updateIfScheduled(id, command.runAt(), MissionAnnouncementContent.text(command.content()),
                Boolean.TRUE.equals(command.push()), MissionAnnouncementContent.text(command.pushTitle()),
                MissionAnnouncementContent.text(command.pushBody()), actor, now())) throw new AnnouncementNotEditableException();
        return response(reader.getById(id));
    }

    @Transactional
    public MissionAnnouncementResponse sendNow(Long roomId, Long missionId, Long id, String actor) {
        RoomMission mission = lockMission(roomId, missionId, true);
        MissionAnnouncement current = lockAnnouncement(mission, id);
        LocalDateTime now = now();
        // 미래 미션의 공개 시각을 앞당기는 우회 경로를 제공하지 않는다.
        if (now.isBefore(mission.opensAt()) || !writer.updateIfScheduled(id, now, current.content(), current.push(),
                current.pushTitle(), current.pushBody(), actor, now)) throw new AnnouncementNotEditableException();
        return response(reader.getById(id));
    }

    @Transactional
    public MissionAnnouncementResponse cancel(Long roomId, Long missionId, Long id, String actor) {
        RoomMission mission = lockMission(roomId, missionId, false);
        lockAnnouncement(mission, id);
        if (!writer.cancelIfScheduled(id, actor, now())) throw new AnnouncementNotEditableException();
        return response(reader.getById(id));
    }

    @Transactional
    public MissionAnnouncementResponse deleteSentMessage(Long roomId, Long missionId, Long id) {
        RoomMission mission = lockMission(roomId, missionId, false);
        MissionAnnouncement item = lockAnnouncement(mission, id);
        if (item.status() != MissionAnnouncementStatus.SENT || item.sentMessageId() == null)
            throw new AnnouncementNotEditableException();
        messageWriter.markAdminDeleted(item.sentMessageId(), now());
        return response(item);
    }

    public org.son.haruharu.domain.mission.chat.business.dto.AnnouncementPageResponse list(Long roomId, Long missionId, Long after, int size) {
        if (size < 1 || size > 100 || after != null && after < 0)
            throw new org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException("size", "페이지 크기는 1~100, 커서는 0 이상이어야 해요");
        missions.getInRoom(roomId, missionId);
        List<MissionAnnouncement> found = reader.page(missionId, after, size + 1);
        List<MissionAnnouncement> items = found.stream().limit(size).toList();
        Map<Long, LocalDateTime> deleted = deletedAt(items);
        return new org.son.haruharu.domain.mission.chat.business.dto.AnnouncementPageResponse(
                items.stream().map(a -> MissionAnnouncementResponse.of(a,
                        a.sentMessageId() == null ? null : deleted.get(a.sentMessageId()))).toList(),
                found.size() > size ? items.getLast().id() : null);
    }

    private Map<Long, LocalDateTime> deletedAt(List<MissionAnnouncement> items) {
        var ids = items.stream().map(MissionAnnouncement::sentMessageId).filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of() : messages.findAllByIds(ids).stream().filter(ChatMessage::isAdminDeleted)
                .collect(Collectors.toMap(ChatMessage::id, ChatMessage::adminDeletedAt));
    }

    private MissionAnnouncementResponse response(MissionAnnouncement item) {
        var deleted = deletedAt(List.of(item));
        return MissionAnnouncementResponse.of(item, item.sentMessageId() == null ? null : deleted.get(item.sentMessageId()));
    }

    /** 모든 쓰기는 방 → 미션 → 안내 순서로 잠근다. 실행·취소·예약 수정이 같은 순서를 사용한다. */
    private RoomMission lockMission(Long roomId, Long missionId, boolean requireUsable) {
        Room room = rooms.findByIdForUpdate(roomId).orElseThrow(RoomNotFoundException::new);
        RoomMission mission = missions.getForUpdateInRoom(roomId, missionId);
        if (requireUsable && (mission.canceledAt() != null || room.statusAt(now()) == RoomStatus.CANCELLED
                || room.statusAt(now()) == RoomStatus.ENDED)) throw new AnnouncementNotEditableException();
        return mission;
    }

    private MissionAnnouncement lockAnnouncement(RoomMission mission, Long id) {
        return reader.getForUpdateInMission(mission, id);
    }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
}
