package org.son.haruharu.admin.domain.room.business;

import org.son.haruharu.admin.domain.room.implement.AdminRoomReader;
import org.son.haruharu.domain.roomschedule.business.RoomScheduleService;
import org.son.haruharu.domain.roomschedule.business.command.ScheduleItemCommand;
import org.son.haruharu.domain.roomschedule.business.dto.RoomScheduleItemResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 어드민 방 편성(#457). 방 존재(ADMIN_ROOM_NOT_FOUND) 확인과 작성자 표기만 하고 core RoomScheduleService 에 맡긴다. */
@Service
@Transactional(readOnly = true)
public class AdminRoomScheduleService {

    private final AdminRoomReader adminRoomReader;
    private final RoomScheduleService roomScheduleService;

    public AdminRoomScheduleService(AdminRoomReader adminRoomReader, RoomScheduleService roomScheduleService) {
        this.adminRoomReader = adminRoomReader;
        this.roomScheduleService = roomScheduleService;
    }

    public List<RoomScheduleItemResponse> list(Long roomId) {
        adminRoomReader.requireExists(roomId);
        return roomScheduleService.list(roomId);
    }

    @Transactional
    public RoomScheduleItemResponse add(Long roomId, ScheduleItemCommand command, Long adminId) {
        adminRoomReader.requireExists(roomId);
        return roomScheduleService.add(roomId, command, actor(adminId));
    }

    @Transactional
    public RoomScheduleItemResponse update(Long roomId, Long itemId, ScheduleItemCommand command, Long adminId) {
        adminRoomReader.requireExists(roomId);
        return roomScheduleService.update(roomId, itemId, command, actor(adminId));
    }

    @Transactional
    public RoomScheduleItemResponse sendNow(Long roomId, Long itemId, Long adminId) {
        adminRoomReader.requireExists(roomId);
        return roomScheduleService.sendNow(roomId, itemId, actor(adminId));
    }

    @Transactional
    public RoomScheduleItemResponse cancel(Long roomId, Long itemId, Long adminId) {
        adminRoomReader.requireExists(roomId);
        return roomScheduleService.cancel(roomId, itemId, actor(adminId));
    }

    @Transactional
    public RoomScheduleItemResponse deleteSentMessage(Long roomId, Long itemId) {
        adminRoomReader.requireExists(roomId);
        return roomScheduleService.deleteSentMessage(roomId, itemId);
    }

    /** RoomInvite.createdBy 와 같은 규약. */
    static String actor(Long adminId) {
        return "admin:" + adminId;
    }
}
