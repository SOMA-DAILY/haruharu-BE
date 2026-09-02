package org.son.haruharu.domain.mission.business;

import org.son.haruharu.domain.mission.implement.MissionMemberStateReader;
import org.son.haruharu.domain.mission.implement.MissionMemberStateSummary;
import org.son.haruharu.domain.mission.implement.MissionMemberStateWriter;
import org.son.haruharu.domain.mission.implement.MissionOutcome;
import org.son.haruharu.domain.mission.implement.MissionOutcomeReader;
import org.son.haruharu.domain.mission.implement.MissionOutcomeWriter;
import org.son.haruharu.domain.mission.implement.RoomMission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 미션 시간창 종료 후 성과를 확정한다.
 * <p>
 * EXPIRED 마감과 outcome 확정은 한 트랜잭션이다. 중간에 끊기면 "카드는 마감됐는데 outcome 은 없는"
 * 상태가 남아 재실행 시 마감 시각이 흔들린다.
 */
@Service
@Transactional(readOnly = true)
public class MissionOutcomeService {

    private final MissionMemberStateWriter memberStateWriter;
    private final MissionMemberStateReader memberStateReader;
    private final MissionOutcomeReader outcomeReader;
    private final MissionOutcomeWriter outcomeWriter;

    public MissionOutcomeService(
            MissionMemberStateWriter memberStateWriter,
            MissionMemberStateReader memberStateReader,
            MissionOutcomeReader outcomeReader,
            MissionOutcomeWriter outcomeWriter
    ) {
        this.memberStateWriter = memberStateWriter;
        this.memberStateReader = memberStateReader;
        this.outcomeReader = outcomeReader;
        this.outcomeWriter = outcomeWriter;
    }

    /**
     * 이미 같은 집계 버전으로 확정됐으면 덮어쓰지 않고 {@link Optional#empty()} 를 반환한다.
     * <p>
     * 배치는 read-only 트랜잭션에서 훑으므로 REQUIRES_NEW 로 쓰기 트랜잭션을 새로 연다.
     * 한 미션의 실패가 바깥 트랜잭션을 rollback-only 로 오염시켜 남은 미션까지 막는 것도 함께 막는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<MissionOutcome> confirm(RoomMission roomMission, LocalDateTime now) {
        if (outcomeReader.isConfirmed(roomMission.id(), MissionOutcome.METRIC_SCHEMA_V1)) {
            return Optional.empty();
        }
        LocalDateTime closesAt = roomMission.closesAt();
        if (closesAt == null) {
            // 슬롯 기반 배정은 closes_at 이 항상 채워진다. null 이면 데이터 불일치이므로 조용히 넘기지 않는다.
            throw new IllegalStateException(
                    "Cannot confirm outcome for a mission without closesAt: roomMissionId="
                            + roomMission.id());
        }

        // 마감을 먼저 찍어야 EXPIRED 가 집계에 반영된다. 순서가 뒤바뀌면 expiredMemberCount 가 항상 0이 된다.
        memberStateWriter.expireUnresolved(roomMission.id(), closesAt);
        MissionMemberStateSummary summary = memberStateReader.summarize(roomMission.id());

        MissionOutcome outcome = MissionOutcome.confirmed(
                roomMission.id(), MissionOutcome.METRIC_SCHEMA_V1, summary, closesAt, now);
        return Optional.of(outcomeWriter.save(outcome));
    }
}
