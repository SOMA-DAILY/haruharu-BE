package org.son.haruharu.domain.mission.business;

import org.son.haruharu.domain.mission.implement.MissionReader;
import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.RoomMission;
import org.son.haruharu.domain.room.implement.Room;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 기본 배정 정책: 매일 오전·오후 각 1개(총 하루 2개)를 방 시작(starts_at) 상대 시각으로 배정한다.
 * <p>
 * "오전/오후"는 벽시계 고정이 아니라 starts_at 상대 offset이다(video 슬롯과 동일 "두 시계 분리" — 저녁 시작 방도
 * 그 방 기준으로 미션이 흐른다). 카탈로그 활성 후보에서 무작위로 뽑되, 방 안에서 문구가 겹치지 않게 뽑는다.
 * 후보가 필요 수보다 적으면 있는 만큼만 배정한다.
 */
@Component
public class FixedTwicePerDayAssigner implements MissionAssigner {

    /** 각 Day의 미션 오픈 offset(시작 시각 기준 경과 시간). 오전 카드=+3h, 오후 카드=+9h. */
    private static final int MORNING_OFFSET_HOURS = 3;
    private static final int AFTERNOON_OFFSET_HOURS = 9;
    private static final int HOURS_PER_DAY = 24;

    private final MissionReader missionReader;
    private final SecureRandom random = new SecureRandom();

    public FixedTwicePerDayAssigner(MissionReader missionReader) {
        this.missionReader = missionReader;
    }

    @Override
    public List<RoomMission> assign(Room room) {
        if (room.startsAt() == null) {
            return List.of(); // 스케줄 시각 없는 구 모델 방은 미션 배정 대상 아님
        }
        int slotCount = room.totalDays() * 2; // 하루 2개(오전·오후)
        List<MissionRevision> picked = pickDistinct(slotCount);
        if (picked.isEmpty()) {
            return List.of();
        }

        List<RoomMission> assigned = new ArrayList<>();
        int sequence = 0;
        for (int day = 0; day < room.totalDays(); day++) {
            long dayBase = (long) day * HOURS_PER_DAY;
            for (int offset : new int[]{MORNING_OFFSET_HOURS, AFTERNOON_OFFSET_HOURS}) {
                if (sequence >= picked.size()) {
                    return assigned; // 후보가 부족하면 있는 만큼만
                }
                LocalDateTime opensAt = room.startsAt().plusHours(dayBase + offset);
                assigned.add(RoomMission.of(room.id(), picked.get(sequence).id(), opensAt, null, sequence));
                sequence++;
            }
        }
        return assigned;
    }

    /** 활성 후보에서 최대 count개를 서로 다르게 무작위로 뽑는다(방 안 문구 중복 방지). */
    private List<MissionRevision> pickDistinct(int count) {
        List<MissionRevision> pool = new ArrayList<>(missionReader.getActiveCandidates());
        List<MissionRevision> picked = new ArrayList<>();
        while (!pool.isEmpty() && picked.size() < count) {
            picked.add(pool.remove(random.nextInt(pool.size())));
        }
        return picked;
    }
}
