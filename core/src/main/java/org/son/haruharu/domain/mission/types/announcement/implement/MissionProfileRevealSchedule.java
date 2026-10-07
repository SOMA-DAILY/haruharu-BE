package org.son.haruharu.domain.mission.types.announcement.implement;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.profile.implement.ProfileField;
import org.son.haruharu.domain.room.implement.ProfileRevealSchedule;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MissionProfileRevealSchedule implements ProfileRevealSchedule {
    private final AnnouncementMissionReader reader;

    @Override
    public boolean isScheduled(Long roomId) {
        // 전부 취소해도 옛 DAY3 공개 규칙으로 돌아가지 않는다.
        return !reader.findRevealPlans(roomId).isEmpty();
    }

    @Override
    public Map<ProfileField, LocalDateTime> revealTimes(Long roomId) {
        Map<ProfileField, LocalDateTime> result = new EnumMap<>(ProfileField.class);
        for (AnnouncementRevealPlan mission : reader.findRevealPlans(roomId)) {
            if (mission.canceledAt() != null) continue;
            for (MissionRevealField field : mission.revealFields()) {
                result.merge(field.profileField(), mission.opensAt(), (a, b) -> a.isBefore(b) ? a : b);
            }
        }
        return result;
    }
}
