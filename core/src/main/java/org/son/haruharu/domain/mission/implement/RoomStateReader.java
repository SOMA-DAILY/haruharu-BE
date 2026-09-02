package org.son.haruharu.domain.mission.implement;

import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * 배정 시점의 방 상태를 확정된 outcome 에서 집계한다.
 * <p>
 * 채팅·영상 원문을 다시 훑지 않고 이미 확정된 {@link MissionOutcome} 만 읽는다. outcome 은 불변이라
 * 같은 시점을 다시 계산해도 값이 흔들리지 않고, Primary metric 과 같은 정의를 공유한다.
 */
@Component
public class RoomStateReader {

    private static final int RATIO_SCALE = 4;

    private final RoomMemberReader roomMemberReader;
    private final MissionOutcomeReader outcomeReader;

    public RoomStateReader(RoomMemberReader roomMemberReader, MissionOutcomeReader outcomeReader) {
        this.roomMemberReader = roomMemberReader;
        this.outcomeReader = outcomeReader;
    }

    public RoomStateSnapshot snapshot(Long roomId, List<RoomMission> previousMissions) {
        int memberCount = roomMemberReader.getByRoomId(roomId).size();
        if (previousMissions.isEmpty()) {
            return RoomStateSnapshot.empty(memberCount);
        }

        List<MissionOutcome> outcomes = outcomeReader.findConfirmed(previousMissions.stream()
                .map(RoomMission::id)
                .toList());
        if (outcomes.isEmpty()) {
            return RoomStateSnapshot.empty(memberCount);
        }

        // sequence 순서를 알 수 없으므로 확정 시각으로 정렬한다. closesAt 은 슬롯 순서와 일치한다.
        List<MissionOutcome> ordered = outcomes.stream()
                .sorted(Comparator.comparing(MissionOutcome::closesAt))
                .toList();
        BigDecimal total = ordered.stream()
                .map(MissionOutcome::roomActivationCoverage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = total.divide(
                BigDecimal.valueOf(ordered.size()), RATIO_SCALE, RoundingMode.HALF_UP);
        int activatedMemberCount = ordered.stream()
                .mapToInt(MissionOutcome::activatedMemberCount)
                .max()
                .orElse(0);

        return new RoomStateSnapshot(
                memberCount,
                ordered.size(),
                activatedMemberCount,
                average,
                ordered.getLast().roomActivationCoverage());
    }
}
