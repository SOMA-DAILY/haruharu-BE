package org.son.haruharu.domain.mission.implement;

import java.time.LocalDateTime;

/**
 * 방별 미션 인스턴스. "어느 방(roomId)에 어떤 미션(missionId)을 언제(opensAt) 노출할지"를 담는다.
 * 방 공용(멤버별 아님) — 완료 추적은 video.mission_id로 개별한다(이번 스코프 밖).
 * opensAt은 절대 시각이라 벽시계 고정이 아니라 방 시작(starts_at) 상대로 배정된다({@link MissionAssigner}).
 * closesAt이 null이면 무기한 열림.
 */
public record RoomMission(
        Long id,
        Long roomId,
        Long missionId,
        LocalDateTime opensAt,
        LocalDateTime closesAt,
        int sequence,
        LocalDateTime createdAt
) {
    public static RoomMission of(Long roomId, Long missionId, LocalDateTime opensAt,
                                 LocalDateTime closesAt, int sequence) {
        return new RoomMission(null, roomId, missionId, opensAt, closesAt, sequence, null);
    }

    /** now가 노출 구간 {@code [opensAt, closesAt)} 안인지. closesAt이 null이면 opensAt 이후 무기한. */
    public boolean isOpenAt(LocalDateTime now) {
        if (now.isBefore(opensAt)) {
            return false;
        }
        return closesAt == null || now.isBefore(closesAt);
    }
}
