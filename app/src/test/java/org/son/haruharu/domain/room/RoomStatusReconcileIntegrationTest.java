package org.son.haruharu.domain.room;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.room.business.RoomStatusReconciler;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * status reconciler end-to-end(#320). 실제 DB(JPA)를 거쳐 저장 status 컬럼이 시각 기준으로 전이되는지 검증한다.
 * 스케줄러({@code RoomReconcileScheduler})는 시각 판단 없이 호출만 하는 얇은 래퍼라 여기서 다루지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class RoomStatusReconcileIntegrationTest {

    @Autowired RoomStatusReconciler reconciler;
    @Autowired RoomWriter roomWriter;
    @Autowired RoomReader roomReader;

    /** 저장 status 는 PENDING 인데 시각은 이미 지난 방 — 배치가 아직 안 돈 상태를 재현한다. */
    private Room persistScheduledRoom(LocalDateTime startsAt) {
        return roomWriter.save(Room.createScheduled(
                startsAt, startsAt.plusDays(2).withHour(18), startsAt.plusDays(2).withHour(21),
                startsAt.plusDays(2).withHour(22), startsAt.plusDays(3).withHour(9), 3));
    }

    @Test
    @DisplayName("시작 시각이 지난 PENDING 방이 실제 DB에서 ACTIVE로 전이된다")
    void reconcile_flipsPendingToActiveInDb() {
        LocalDateTime startsAt = LocalDateTime.now().minusHours(1);
        Room saved = persistScheduledRoom(startsAt);
        assertThat(saved.status()).isEqualTo(RoomStatus.PENDING);

        int changed = reconciler.reconcile(LocalDateTime.now());

        assertThat(changed).isPositive();
        assertThat(roomReader.findById(saved.id()).orElseThrow().status()).isEqualTo(RoomStatus.ACTIVE);
    }

    @Test
    @DisplayName("두 번 돌려도 두 번째에는 아무 것도 바뀌지 않는다 (멱등)")
    void reconcile_isIdempotent() {
        persistScheduledRoom(LocalDateTime.now().minusHours(1));

        reconciler.reconcile(LocalDateTime.now());
        int secondRun = reconciler.reconcile(LocalDateTime.now());

        assertThat(secondRun).isZero();
    }

    @Test
    @DisplayName("취소된 방은 시각이 지나도 전이되지 않는다 (sticky)")
    void reconcile_skipsCancelledRoom() {
        Room saved = persistScheduledRoom(LocalDateTime.now().minusHours(1));
        roomWriter.save(saved.cancelled());

        reconciler.reconcile(LocalDateTime.now());

        assertThat(roomReader.findById(saved.id()).orElseThrow().status()).isEqualTo(RoomStatus.CANCELLED);
    }

    @Test
    @DisplayName("종료 시각이 지난 방은 ENDED로 전이되고, 이후 조회 대상에서 빠진다")
    void reconcile_flipsToEndedAndDropsFromCandidates() {
        Room saved = persistScheduledRoom(LocalDateTime.now().minusDays(5));

        reconciler.reconcile(LocalDateTime.now());

        assertThat(roomReader.findById(saved.id()).orElseThrow().status()).isEqualTo(RoomStatus.ENDED);
        // ENDED·CANCELLED 는 sticky 라 다음 배치의 조회 대상에서 제외된다.
        assertThat(roomReader.getReconcilable()).noneMatch(r -> r.id().equals(saved.id()));
    }

    @Test
    @DisplayName("스케줄 시각이 없는 구 모델 방은 reconciler 조회 대상이 아니다")
    void reconcile_ignoresLegacyRoom() {
        Room legacy = roomWriter.save(Room.create(LocalDateTime.now().toLocalDate(), 3));

        assertThat(roomReader.getReconcilable()).noneMatch(r -> r.id().equals(legacy.id()));
    }

    // ── 구 배치 조회가 새 모델 방을 구조적으로 제외하는지 (실 쿼리로 검증) ──

    @Test
    @DisplayName("getLegacyActive 는 ACTIVE 여도 새 모델 방을 반환하지 않는다 (고정 cron 배치 격리)")
    void getLegacyActive_excludesScheduledRoom() {
        Room scheduled = persistScheduledRoom(LocalDateTime.now().minusHours(1));
        reconciler.reconcile(LocalDateTime.now()); // ACTIVE 로 materialize
        Room legacyActive = roomWriter.save(Room.create(LocalDate.now(), 3).started(LocalDateTime.now()));

        assertThat(roomReader.getLegacyActive())
                .noneMatch(r -> r.id().equals(scheduled.id()))
                .anyMatch(r -> r.id().equals(legacyActive.id()));
    }

    @Test
    @DisplayName("ACTIVE 전이 시 startedAt 이 starts_at 으로 채워져 오늘 화면 계산이 NPE 없이 동작한다")
    void reconcile_anchorsStartedAt() {
        LocalDateTime startsAt = LocalDateTime.now().minusHours(1).withNano(0);
        Room saved = persistScheduledRoom(startsAt);

        reconciler.reconcile(LocalDateTime.now());

        Room reloaded = roomReader.findById(saved.id()).orElseThrow();
        assertThat(reloaded.startedAt()).isNotNull();
        assertThatCode(() -> reloaded.dateOfDay(1)).doesNotThrowAnyException();
        assertThatCode(() -> reloaded.announcedAt()).doesNotThrowAnyException();
    }

    // ── Codex 리뷰 반영: 배치 지연·장기 중단 구간의 경계 조건 (실 DB) ──

    @Test
    @DisplayName("배치가 오래 멈춰 PENDING에서 ENDED로 직행해도 startedAt이 앵커된다")
    void reconcile_anchorsStartedAtEvenWhenSkippingActive() {
        // 5일 전 시작 → 이미 ends_at 도 지난 방. 저장 status 는 PENDING(배치가 그동안 안 돎).
        Room saved = persistScheduledRoom(LocalDateTime.now().minusDays(5));
        assertThat(saved.status()).isEqualTo(RoomStatus.PENDING);

        reconciler.reconcile(LocalDateTime.now());

        Room reloaded = roomReader.findById(saved.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(RoomStatus.ENDED);
        // 앵커가 비면 MatchResultService 가 "미시작"으로 보고 결과 발표를 영영 숨긴다.
        assertThat(reloaded.startedAt()).isNotNull();
    }

    @Test
    @DisplayName("ends_at이 지났지만 배치 전이면, 저장 status가 ACTIVE여도 내 현재 방에서 제외된다")
    void findCurrent_excludesStaleEndedRoom() {
        Room saved = persistScheduledRoom(LocalDateTime.now().minusDays(5));
        roomWriter.save(saved.materialized(RoomStatus.ACTIVE)); // 배치가 ACTIVE 까지만 반영한 상태 재현

        assertThat(roomReader.findCurrent(List.of(saved.id()), LocalDateTime.now())).isEmpty();
    }
}
