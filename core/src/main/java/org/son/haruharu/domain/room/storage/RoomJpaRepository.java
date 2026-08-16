package org.son.haruharu.domain.room.storage;

import org.son.haruharu.domain.room.implement.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface RoomJpaRepository extends JpaRepository<RoomEntity, Long> {

    List<RoomEntity> findByIdInAndStatusNot(List<Long> ids, RoomStatus status);

    List<RoomEntity> findAllByScheduledDateAndStatusAndStartsAtIsNull(
            LocalDate scheduledDate, RoomStatus status);

    // #320 구 모델(자동매칭) 전용 배치 대상. 초대코드 모델 방(starts_at 보유)은 고정 벽시계 배치가 아니라
    // RoomStatusReconciler 가 방별 시각으로 처리하므로 조회 단계에서 잘라낸다.
    List<RoomEntity> findAllByStatusAndStartsAtIsNull(RoomStatus status);

    // #320 reconciler 대상: 스케줄 시각을 가진(초대코드 모델) 방 중 아직 확정 종료되지 않은 것.
    // ENDED·CANCELLED 는 sticky 라 다시 볼 필요가 없어 조회 단계에서 잘라낸다.
    List<RoomEntity> findAllByStartsAtIsNotNullAndStatusNotIn(Collection<RoomStatus> statuses);
}
