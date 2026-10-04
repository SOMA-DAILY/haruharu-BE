package org.son.haruharu.admin.domain.room.business;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.admin.domain.room.business.dto.OperatorMissionPageResponse;
import org.son.haruharu.admin.domain.room.implement.AdminRoomReader;
import org.son.haruharu.domain.mission.business.OperatorMissionService;
import org.son.haruharu.domain.mission.business.command.OperatorMissionCommand;
import org.son.haruharu.domain.mission.implement.OperatorMission;
import org.son.haruharu.domain.mission.implement.OperatorMissionTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminOperatorMissionService {
    private final AdminRoomReader roomReader;
    private final OperatorMissionService missions;
    private final Clock clock;
    private final org.son.haruharu.domain.mission.business.MissionImageService images;

    public OperatorMissionPageResponse history(Long roomId, Long cursor, int size) {
        var room = roomReader.getById(roomId);
        int limit = Math.min(Math.max(size, 1), 50);
        var rows = missions.history(roomId, cursor, limit + 1);
        boolean more = rows.size() > limit;
        var page = more ? rows.subList(0, limit) : rows;
        var photos = images.views(page.stream().map(OperatorMission::imageId).toList());
        var now = LocalDateTime.now(clock);
        return new OperatorMissionPageResponse(roomId, room.statusAt(now), room.startsAt(),
                room.finalSelectionOpensAt(), now, OperatorMissionTemplate.DEFAULTS, page.stream().map(item -> org.son.haruharu.admin.domain.room.business.dto.OperatorMissionResponse.from(item,photos.get(item.imageId()))).toList(),
                more ? page.getLast().id() : null);
    }
    @Transactional public org.son.haruharu.admin.domain.room.business.dto.OperatorMissionResponse create(Long roomId, Long adminId, OperatorMissionCommand command) {
        return view(missions.create(roomId, adminId, command));
    }
    @Transactional public org.son.haruharu.admin.domain.room.business.dto.OperatorMissionResponse update(Long roomId, Long id, OperatorMissionCommand command) {
        return view(missions.update(roomId, id, command));
    }
    @Transactional public org.son.haruharu.admin.domain.room.business.dto.OperatorMissionResponse cancel(Long roomId, Long id) { return view(missions.cancel(roomId, id)); }
    @Transactional public org.son.haruharu.admin.domain.room.business.dto.OperatorMissionResponse delete(Long roomId, Long id) { return view(missions.delete(roomId, id)); }
    @Transactional
    public org.son.haruharu.domain.mission.business.dto.MissionImageResponse upload(Long roomId, Long adminId, byte[] bytes, String contentType) {
        roomReader.getById(roomId);
        return images.upload(roomId,adminId,bytes,contentType);
    }
    private org.son.haruharu.admin.domain.room.business.dto.OperatorMissionResponse view(OperatorMission mission) {
        return org.son.haruharu.admin.domain.room.business.dto.OperatorMissionResponse.from(mission,images.view(mission.imageId()));
    }
}
