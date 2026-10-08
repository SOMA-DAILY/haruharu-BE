package org.son.haruharu.domain.mission.common.business;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.common.ApiFieldError;
import org.son.haruharu.domain.mission.chat.business.MissionAnnouncementService;
import org.son.haruharu.domain.mission.chat.business.command.MissionAnnouncementCommand;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncement;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementReader;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementStatus;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementWriter;
import org.son.haruharu.domain.mission.common.business.command.RoomMissionCommand;
import org.son.haruharu.domain.mission.common.business.dto.MissionPageResponse;
import org.son.haruharu.domain.mission.common.business.dto.RoomMissionResponse;
import org.son.haruharu.domain.mission.common.business.exception.MissionAnnouncementConflictException;
import org.son.haruharu.domain.mission.common.business.exception.MissionNotEditableException;
import org.son.haruharu.domain.mission.common.implement.MissionTypeRegistry;
import org.son.haruharu.domain.mission.common.implement.RoomMission;
import org.son.haruharu.domain.mission.common.implement.RoomMissionReader;
import org.son.haruharu.domain.mission.common.implement.RoomMissionWriter;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomMissionService {
    private final RoomReader rooms;
    private final RoomMissionReader reader;
    private final RoomMissionWriter writer;
    private final MissionAnnouncementReader announcements;
    private final MissionAnnouncementWriter announcementWriter;
    private final MissionAnnouncementService announcementService;
    private final RoomMemberReader members;
    private final MissionTypeRegistry types;
    private final Clock clock;

    @Transactional
    public RoomMissionResponse create(Long roomId, RoomMissionCommand command, String actor) {
        Room room = lockRoom(roomId);

        RoomMission mission = definition(roomId, command, actor);

        types.get(mission.type()).validate(mission.details());
        validateInitialAnnouncement(mission, command);

        RoomMission saved = writer.save(mission);

        if (command.announcement() != null) announcementService.add(roomId, saved.id(), command.announcement(), actor);

        return response(saved, room, now());
    }

    /** 방·초대코드 생성과 같은 트랜잭션. 모든 행의 검증 오류에 요청 위치를 붙인다. */
    @Transactional
    public List<RoomMissionResponse> createAll(Long roomId, List<RoomMissionCommand> commands, String actor) {
        if (commands == null || commands.isEmpty()) return List.of();

        if (commands.size() > 100) throw new InvalidMissionException("missions", "한 번에 100개까지 생성할 수 있어요");

        List<ApiFieldError> errors = new ArrayList<>();

        for (int i = 0; i < commands.size(); i++) {
            try {
                var mission = definition(roomId, commands.get(i), actor);
                types.get(mission.type()).validate(mission.details());
                validateInitialAnnouncement(mission, commands.get(i));
            } catch (InvalidMissionException e) {
                String prefix = "missions[" + i + "].";
                e.getFieldErrors().forEach(error -> errors.add(new ApiFieldError(prefix + error.field(), error.message())));
            }

        }

        if (!errors.isEmpty()) throw new InvalidMissionException(errors);

        return commands.stream().map(command -> create(roomId, command, actor)).toList();
    }

    @Transactional
    public RoomMissionResponse update(Long roomId, Long missionId, RoomMissionCommand command, String actor) {
        Room room = lockRoom(roomId);

        RoomMission current = lockMission(roomId, missionId);

        LocalDateTime now = now();
        if (current.canceledAt() != null || !now.isBefore(current.opensAt())
                || room.statusAt(now) == RoomStatus.CANCELLED || room.statusAt(now) == RoomStatus.ENDED)
            throw new MissionNotEditableException();
        if (command.type() != current.type() || command.announcement() != null)
            throw new InvalidMissionException("type", "유형은 변경할 수 없으며 안내는 별도 API로 수정해 주세요");
        RoomMission updated = current.update(command.details(), command.opensAt(), command.closesAt(), actor, now);
        types.get(updated.type()).validate(updated.details());
        // 정의 변경으로 기존 예약의 게시 시각이나 본문 생략 조건이 깨지지 않게 한다.
        for (MissionAnnouncement announcement : announcements.findByMissionId(missionId)) {
            if (announcement.status() == MissionAnnouncementStatus.SCHEDULED) {
                try {
                    announcementService.validate(updated,
                            new MissionAnnouncementCommand(
                                    announcement.announcementType(), announcement.runAt(), announcement.content(),
                                    announcement.push(), announcement.pushTitle(), announcement.pushBody()));
                } catch (InvalidMissionException e) {
                    // 저장된 안내의 필드는 이 PUT의 입력 경로가 아니므로 업무 충돌로 반환한다.
                    throw new MissionAnnouncementConflictException();
                }
            }
        }
        return response(writer.save(updated), room, now);
    }

    @Transactional
    public RoomMissionResponse cancel(Long roomId, Long missionId, String actor) {
        Room room = lockRoom(roomId);
        RoomMission mission = lockMission(roomId, missionId);
        LocalDateTime now = now();
        RoomMission canceled = mission.canceledAt() == null ? writer.save(mission.cancel(actor, now)) : mission;
        announcementWriter.cancelScheduledForMission(missionId, actor, now);
        return response(canceled, room, now);
    }

    public RoomMissionResponse get(Long roomId, Long missionId) {
        Room room = room(roomId);
        return response(reader.getInRoom(roomId, missionId), room, now());
    }

    public MissionPageResponse list(Long roomId, Long after, int size) {
        validatePage(after, size);
        Room room = room(roomId);
        return page(reader.page(roomId, after, size + 1), room, size, now());
    }

    // 일정과 유형별 정의는 같은 행이라 한 번의 조회로 일관되게 읽힌다. 공개 판정은 진입 시각으로 고정한다.
    public RoomMissionResponse getForParticipant(Long roomId, Long missionId, Long userId) {
        LocalDateTime now = now();
        Room room = room(roomId);
        members.getByRoomIdAndUserId(roomId, userId);
        RoomMission mission = reader.getInRoom(roomId, missionId);
        reader.requireVisible(mission, room, now);
        return response(mission, room, now);
    }

    public MissionPageResponse listForParticipant(Long roomId, Long userId, Long after, int size) {
        LocalDateTime now = now();
        validatePage(after, size);
        Room room = room(roomId);
        members.getByRoomIdAndUserId(roomId, userId);
        if (room.statusAt(now) == RoomStatus.PENDING || room.startsAt() != null && now.isBefore(room.startsAt())) return new MissionPageResponse(List.of(), null);
        return page(reader.visiblePage(roomId, after, now, size + 1), room, size, now);
    }

    public List<RoomMissionResponse> findForParticipant(Long roomId, Long userId, List<Long> ids) {
        LocalDateTime now = now();
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(id -> id == null || id <= 0))
            throw new InvalidMissionException("ids", "미션 ID를 1개부터 100개까지 지정해 주세요");
        Room room = room(roomId);
        members.getByRoomIdAndUserId(roomId, userId);
        return reader.findByIdsInRoom(roomId, ids).stream().filter(m -> m.visibleAt(now, room))
                .map(m -> response(m, room, now)).toList();
    }

    private RoomMission definition(Long roomId, RoomMissionCommand command, String actor) {
        if (command == null) throw new InvalidMissionException("mission", "미션 내용을 입력해 주세요");
        return RoomMission.create(roomId, command.type(), command.details(), command.opensAt(), command.closesAt(), actor);
    }

    private void validateInitialAnnouncement(RoomMission mission, RoomMissionCommand command) {
        MissionAnnouncementCommand announcement = command.announcement();
        if (announcement == null) return;
        try {
            announcementService.validate(mission, announcement);
        } catch (InvalidMissionException e) {
            throw new InvalidMissionException(e.getFieldErrors().stream()
                    .map(error -> new ApiFieldError("announcement." + error.field(), error.message())).toList());
        }
    }

    private MissionPageResponse page(List<RoomMission> missions, Room room, int size, LocalDateTime now) {
        boolean more = missions.size() > size;
        var selected = missions.stream().limit(size).toList();
        var items = selected.stream().map(m -> response(m, room, now)).toList();
        return new MissionPageResponse(items, more ? items.getLast().missionId() : null);
    }

    private void validatePage(Long after, int size) {
        if (size < 1 || size > 100 || after != null && after < 0)
            throw new InvalidMissionException("size", "페이지 크기는 1~100, 커서는 0 이상이어야 해요");
    }

    private RoomMissionResponse response(RoomMission mission, Room room, LocalDateTime now) {
        return RoomMissionResponse.of(mission, room, now, types.get(mission.type()));
    }

    private Room room(Long id) { return rooms.findById(id).orElseThrow(RoomNotFoundException::new); }
    private Room lockRoom(Long id) { return rooms.findByIdForUpdate(id).orElseThrow(RoomNotFoundException::new); }
    private RoomMission lockMission(Long roomId, Long id) {
        return reader.getForUpdateInRoom(roomId, id);
    }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
}
