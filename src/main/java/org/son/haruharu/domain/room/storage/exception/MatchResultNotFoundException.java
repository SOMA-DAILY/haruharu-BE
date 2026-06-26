package org.son.haruharu.domain.room.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class MatchResultNotFoundException extends NotFoundException {

    public MatchResultNotFoundException() {
        super("ROOM_MATCH_404_NOT_FOUND", "아직 성사된 매칭이 없습니다.");
    }
}
