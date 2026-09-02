package org.son.haruharu.domain.mission.business;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.son.haruharu.domain.mission.implement.MissionOutcome;
import org.son.haruharu.domain.mission.implement.MissionOutcomeReader;
import org.son.haruharu.domain.mission.implement.RoomMission;
import org.son.haruharu.domain.mission.implement.RoomMissionReader;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 시간창이 닫힌 미션을 훑어 outcome 을 확정한다.
 * <p>
 * {@code settleDelay} 만큼 여유를 두고 집계한다. 활동 이벤트가 AFTER_COMMIT 으로 발행되므로
 * 마감 직전 활동이 늦게 도착할 수 있다. 판정 기준은 도착 시각이 아니라 occurredAt 이며,
 * 그 필터는 활동 기록 시점에 이미 적용된다.
 */
@Service
@Transactional(readOnly = true)
public class MissionOutcomeBatchService {

    private static final Logger log = LoggerFactory.getLogger(MissionOutcomeBatchService.class);

    private final RoomMissionReader roomMissionReader;
    private final MissionOutcomeReader outcomeReader;
    private final MissionOutcomeService outcomeService;

    public MissionOutcomeBatchService(
            RoomMissionReader roomMissionReader,
            MissionOutcomeReader outcomeReader,
            MissionOutcomeService outcomeService) {
        this.roomMissionReader = roomMissionReader;
        this.outcomeReader = outcomeReader;
        this.outcomeService = outcomeService;
    }

    public MissionOutcomeBatchResult processClosedMissions(
            LocalDateTime now, Duration settleDelay, int batchSize) {
        LocalDateTime closedBefore = now.minus(settleDelay);
        List<RoomMission> targets = roomMissionReader.getClosedWithoutOutcome(
                closedBefore, MissionOutcome.METRIC_SCHEMA_V1, batchSize);

        int examined = 0;
        int confirmed = 0;
        int skipped = 0;
        int failed = 0;
        for (RoomMission roomMission : targets) {
            examined++;
            try {
                if (outcomeService.confirm(roomMission, now).isPresent()) {
                    confirmed++;
                } else {
                    skipped++;
                }
            } catch (DataIntegrityViolationException integrityFailure) {
                // 무결성 위반은 두 가지다. 유니크 충돌(다른 실행이 먼저 확정)만 멱등 스킵이고,
                // CHECK/FK 위반(예: EXPIRED 마이그레이션 미적용)은 진짜 실패다. 실제 확정 여부로 판별한다.
                // 뭉뚱그리면 outcome 이 영영 안 쌓이는데 모니터링에는 failed=0 으로 보인다.
                Boolean alreadyConfirmed = recheckConfirmed(roomMission);
                if (alreadyConfirmed == null) {
                    failed++;
                    log.atError()
                            .addKeyValue("event", "MISSION_OUTCOME_CONFIRM_RECHECK_FAILED")
                            .addKeyValue("roomMissionId", roomMission.id())
                            .addKeyValue("errorType", integrityFailure.getClass().getSimpleName())
                            .log("미션 outcome 확정 여부 재확인 실패");
                } else if (alreadyConfirmed) {
                    skipped++;
                    log.atInfo()
                            .addKeyValue("event", "MISSION_OUTCOME_IDEMPOTENT_SKIP")
                            .addKeyValue("roomMissionId", roomMission.id())
                            .log("동시 outcome 확정 레이스 멱등 스킵");
                } else {
                    failed++;
                    log.atError()
                            .addKeyValue("event", "MISSION_OUTCOME_INTEGRITY_VIOLATION")
                            .addKeyValue("roomMissionId", roomMission.id())
                            .addKeyValue("errorType", integrityFailure.getClass().getSimpleName())
                            .log("미션 outcome 무결성 위반 — 레이스가 아니라 확정 실패");
                }
            } catch (RuntimeException unexpected) {
                failed++;
                log.atError()
                        .addKeyValue("event", "MISSION_OUTCOME_CONFIRM_FAILED")
                        .addKeyValue("roomMissionId", roomMission.id())
                        .addKeyValue("errorType", unexpected.getClass().getSimpleName())
                        .log("미션 outcome 확정 실패");
            }
        }
        return new MissionOutcomeBatchResult(examined, confirmed, skipped, failed);
    }

    /** 확정 여부. 재확인 자체가 실패하면 판별 불가라는 뜻으로 null 을 돌려준다. */
    private Boolean recheckConfirmed(RoomMission roomMission) {
        try {
            return outcomeReader.isConfirmed(roomMission.id(), MissionOutcome.METRIC_SCHEMA_V1);
        } catch (DataAccessException recheckFailure) {
            return null;
        }
    }
}
