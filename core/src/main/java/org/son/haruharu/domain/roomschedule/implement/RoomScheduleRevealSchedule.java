package org.son.haruharu.domain.roomschedule.implement;

import org.son.haruharu.domain.profile.implement.ProfileField;
import org.son.haruharu.domain.room.implement.ProfileRevealSchedule;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

/** room 의 ProfileRevealSchedule 을 편성 행으로 구현한다. */
@Component
public class RoomScheduleRevealSchedule implements ProfileRevealSchedule {

    private final RoomScheduleItemReader itemReader;

    public RoomScheduleRevealSchedule(RoomScheduleItemReader itemReader) {
        this.itemReader = itemReader;
    }

    @Override
    public boolean isScheduled(Long roomId) {
        return itemReader.hasRevealSchedule(roomId);
    }

    @Override
    public Map<ProfileField, LocalDateTime> revealTimes(Long roomId) {
        Map<ProfileField, LocalDateTime> times = new EnumMap<>(ProfileField.class);
        itemReader.findRevealTimes(roomId).forEach((field, at) -> times.put(field.profileField(), at));
        return times;
    }
}
