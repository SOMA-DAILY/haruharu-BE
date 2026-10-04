package org.son.haruharu.domain.room.implement;

import org.son.haruharu.domain.profile.implement.ProfileField;

import java.time.LocalDateTime;
import java.util.Map;

/** 방 멤버 프로필 항목의 공개 시각. room 이 정의하고 roomschedule 이 구현한다(room → roomschedule 참조 없음). */
public interface ProfileRevealSchedule {

    /** 편성으로 공개를 정하는 방인지. 공개 행을 전부 취소한 방도 true(옛 DAY3 규칙으로 돌아가지 않는다). */
    boolean isScheduled(Long roomId);

    /** 항목별 공개 시각 = 취소가 아닌 행 중 가장 이른 run_at. 편성에 없는 항목은 키가 없다. */
    Map<ProfileField, LocalDateTime> revealTimes(Long roomId);
}
