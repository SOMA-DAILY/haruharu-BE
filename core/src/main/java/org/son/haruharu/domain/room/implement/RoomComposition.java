package org.son.haruharu.domain.room.implement;

import org.son.haruharu.common.ApiFieldError;
import org.son.haruharu.domain.room.implement.exception.InvalidRoomCompositionException;

import java.util.ArrayList;
import java.util.List;

/**
 * 방 편성 정보(#513): 어느 기수의 방인지, 운영진이 부르는 이름, 성별별 정원. 모두 선택이다 — 기존 방·웹 전용 방은 비운다.
 * <p>
 * 성별 정원은 앱 신청자 배정(#516)이 지키는 방 전체 정원이다. 초대코드 정원(room_invites.max_uses)과는 따로 간다.
 * <p>
 * <b>새 값은 {@link #of} 로만 만든다</b> — 검증은 거기서 한다. 생성자는 DB 복원용이라 검증하지 않는다:
 * DB 에 CHECK 가 없으므로 직접 SQL 로 잘못된 값이 들어가도 방 조회가 깨지지 않게 하고, 감사는 513 verify SQL 이 한다.
 *
 * @param cohortId       기수 id. 웹 전용 방이면 null
 * @param name           운영진용 방 이름("서울 6기 1조"). 앱 화면에는 쓰지 않는다
 * @param maleCapacity   남성 정원. null 이면 정하지 않음
 * @param femaleCapacity 여성 정원. null 이면 정하지 않음
 */
public record RoomComposition(Long cohortId, String name, Integer maleCapacity, Integer femaleCapacity) {

    public static final RoomComposition NONE = new RoomComposition(null, null, null, null);
    static final int NAME_MAX = 50;

    /** 입력값 검증·정리. 이름 앞뒤 공백은 지우고, 빈 이름은 이름 없음으로 본다. */
    public static RoomComposition of(Long cohortId, String name, Integer maleCapacity, Integer femaleCapacity) {
        String trimmed = name == null || name.isBlank() ? null : name.strip();
        List<ApiFieldError> errors = new ArrayList<>();
        if (trimmed != null && trimmed.length() > NAME_MAX) {
            errors.add(new ApiFieldError("name", "방 이름은 " + NAME_MAX + "자 이하여야 합니다."));
        }
        if (maleCapacity != null && maleCapacity < 1) {
            errors.add(new ApiFieldError("male_capacity", "남성 정원은 1 이상이어야 합니다."));
        }
        if (femaleCapacity != null && femaleCapacity < 1) {
            errors.add(new ApiFieldError("female_capacity", "여성 정원은 1 이상이어야 합니다."));
        }
        if (!errors.isEmpty()) {
            throw new InvalidRoomCompositionException(errors);
        }
        return new RoomComposition(cohortId, trimmed, maleCapacity, femaleCapacity);
    }
}
