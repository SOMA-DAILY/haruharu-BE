package org.son.haruharu.admin.domain.room.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class AdminRoomOrganizationInvalidException extends BadRequestException {
    public AdminRoomOrganizationInvalidException() {
        super("ADMIN_ROOM_ORGANIZATION_INVALID", "기수 또는 방 구분을 선택해 주세요.");
    }
}
