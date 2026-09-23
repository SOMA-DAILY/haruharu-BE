package org.son.haruharu.domain.meta.business;

import org.son.haruharu.domain.meta.business.dto.MetaResponse;
import org.son.haruharu.domain.room.implement.Room;
import org.springframework.stereotype.Service;

/** 정책 상수 메타 조회. 설정값 매핑만 하므로 영속성 접근·트랜잭션이 없다. */
@Service
public class MetaService {

    public MetaResponse getMeta() {
        return new MetaResponse(
                // 방 시작 시각·세션 일수의 원본 상수를 그대로 노출한다(하드코딩 중복 금지)
                new MetaResponse.SessionMeta(Room.DAY_START_HOUR, Room.DEFAULT_TOTAL_DAYS));
    }
}
