package org.son.haruharu.domain.room.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RoomReader {

    private final RoomRepository roomRepository;

    public Optional<Room> findById(Long roomId) {
        return roomRepository.findById(roomId);
    }

    public Optional<Room> findByIdForUpdate(Long roomId) {
        return roomRepository.findByIdForUpdate(roomId);
    }

    /**
     * 후보 방 id들 중 종료되지 않은 <b>대표 현재 방</b>을 단일 조회로 찾는다. 다중 방 참여(#374)에서는 유저가
     * 여러 현재 방을 가질 수 있으므로, 상태 우선순위(statusRank: ACTIVE→SELECTING→PENDING) 후 roomId tie-break 으로
     * 결정적 단일 결과를 보장한다.
     * <p>
     * 저장 status 로 1차 필터한 뒤 <b>시각 기준으로 한 번 더 거른다</b>(#320). 새 모델 방은 ends_at 이 지나도
     * 5분 배치가 돌기 전까지 저장 status 가 ACTIVE 라, 저장값만 믿으면 끝난 방이 '내 현재 방'으로 잡힌다.
     * 정확도는 배치가 아니라 읽기가 보장한다.
     */
    public Optional<Room> findCurrent(List<Long> candidateRoomIds, LocalDateTime now) {
        return findCurrentAll(candidateRoomIds, now).stream().findFirst();
    }

    /**
     * 후보 방 id들 중 시각 기준으로 현재(PENDING, ACTIVE, SELECTING)인 방을 모두 반환한다.
     * 대표 방이 필요한 기존 API도 이 순서를 공유하도록 {@link #findCurrent(List, LocalDateTime)}가 위임한다.
     */
    public List<Room> findCurrentAll(List<Long> candidateRoomIds, LocalDateTime now) {
        if (candidateRoomIds.isEmpty()) {
            return List.of();
        }
        return roomRepository.findByIdIn(candidateRoomIds).stream()
                .filter(room -> isCurrent(room.statusAt(now)))
                .sorted(Comparator.comparingInt((Room room) -> statusRank(room.statusAt(now)))
                        .thenComparing(Room::id))
                .toList();
    }

    private boolean isCurrent(RoomStatus status) {
        return status == RoomStatus.PENDING || status == RoomStatus.ACTIVE || status == RoomStatus.SELECTING;
    }

    private int statusRank(RoomStatus status) {
        return switch (status) {
            case ACTIVE -> 0;
            case SELECTING -> 1;
            case PENDING -> 2;
            default -> 3;
        };
    }

    /**
     * 09:00 자동 시작 배치용.
     * 초대코드 모델 방(starts_at 보유)은 제외한다(#320) — scheduledDate 가 starts_at 의 날짜라 그대로 두면
     * 19:00 시작 예정 방이 09:00 에 조기 flip 된다. 그쪽 전이는 RoomStatusReconciler 담당.
     */
    public List<Room> getScheduledPending(LocalDate scheduledDate) {
        return roomRepository.findAllByScheduledDateAndStatus(scheduledDate, RoomStatus.PENDING);
    }

    /**
     * 고정 벽시계 배치(종료·위로구름·슬롯 푸시·최종선택 오픈)의 진입점(#320).
     * <b>구 모델 방만</b> 반환한다 — 초대코드 모델 방은 방마다 시각이 달라 9시·18:05 같은 고정 cron 으로
     * 처리하면 조기 종료·오지급·엉뚱한 시각 푸시가 난다. 그쪽은 RoomStatusReconciler 담당.
     * <p>
     * 호출부마다 {@code startsAt == null} 필터를 흩뿌리는 대신 조회를 하나로 묶어 누락을 구조적으로 막는다.
     */
    public List<Room> getLegacyActive() {
        return roomRepository.findLegacyActive();
    }

    /**
     * status reconciler 대상(#320). 스케줄 시각을 가진 방 중 ENDED·CANCELLED(sticky 확정 상태)를 뺀 것.
     */
    public List<Room> getReconcilable() {
        return roomRepository.findReconcilable();
    }
}
