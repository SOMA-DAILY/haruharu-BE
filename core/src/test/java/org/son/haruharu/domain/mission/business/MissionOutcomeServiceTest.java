package org.son.haruharu.domain.mission.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.mission.implement.MissionMemberStateReader;
import org.son.haruharu.domain.mission.implement.MissionMemberStateSummary;
import org.son.haruharu.domain.mission.implement.MissionMemberStateWriter;
import org.son.haruharu.domain.mission.implement.MissionOutcome;
import org.son.haruharu.domain.mission.implement.MissionOutcomeReader;
import org.son.haruharu.domain.mission.implement.MissionOutcomeWriter;
import org.son.haruharu.domain.mission.implement.RoomMission;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MissionOutcomeServiceTest {

    private static final Long ROOM_MISSION_ID = 10L;
    private static final LocalDateTime OPENS_AT = LocalDateTime.of(2026, 9, 1, 12, 0);
    private static final LocalDateTime CLOSES_AT = LocalDateTime.of(2026, 9, 1, 18, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 18, 5);

    @Mock MissionMemberStateWriter memberStateWriter;
    @Mock MissionMemberStateReader memberStateReader;
    @Mock MissionOutcomeReader outcomeReader;
    @Mock MissionOutcomeWriter outcomeWriter;

    MissionOutcomeService outcomeService;

    @BeforeEach
    void setUp() {
        outcomeService = new MissionOutcomeService(
                memberStateWriter, memberStateReader, outcomeReader, outcomeWriter);
    }

    private RoomMission roomMission() {
        return new RoomMission(ROOM_MISSION_ID, 1L, 100L, OPENS_AT, CLOSES_AT, 0, OPENS_AT);
    }

    @Test
    @DisplayName("시간창이 닫힌 미션은 미해소 카드를 EXPIRED로 마감한 뒤 outcome이 확정된다")
    void givenClosedMission_whenConfirm_thenExpiresThenConfirms() {
        // given
        given(outcomeReader.isConfirmed(ROOM_MISSION_ID, MissionOutcome.METRIC_SCHEMA_V1))
                .willReturn(false);
        given(memberStateReader.summarize(ROOM_MISSION_ID))
                .willReturn(new MissionMemberStateSummary(6, 5, 4, 3, 1, 1));
        given(outcomeWriter.save(any(MissionOutcome.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        Optional<MissionOutcome> confirmed = outcomeService.confirm(roomMission(), NOW);

        // then
        verify(memberStateWriter).expireUnresolved(ROOM_MISSION_ID, CLOSES_AT);

        ArgumentCaptor<MissionOutcome> captor = ArgumentCaptor.forClass(MissionOutcome.class);
        verify(outcomeWriter).save(captor.capture());
        MissionOutcome saved = captor.getValue();
        assertThat(saved.roomMissionId()).isEqualTo(ROOM_MISSION_ID);
        assertThat(saved.metricSchemaVersion()).isEqualTo(MissionOutcome.METRIC_SCHEMA_V1);
        assertThat(saved.targetMemberCount()).isEqualTo(6);
        assertThat(saved.activatedMemberCount()).isEqualTo(4);
        assertThat(saved.expiredMemberCount()).isEqualTo(1);
        assertThat(saved.roomActivationCoverage()).isEqualByComparingTo(new BigDecimal("0.6667"));
        assertThat(saved.closesAt()).isEqualTo(CLOSES_AT);
        assertThat(saved.calculatedAt()).isEqualTo(NOW);
        assertThat(confirmed).isPresent();
    }

    @Test
    @DisplayName("EXPIRED 마감은 집계보다 먼저 실행돼 마감 결과가 집계에 반영된다")
    void givenClosedMission_whenConfirm_thenExpireRunsBeforeSummarize() {
        // given
        given(outcomeReader.isConfirmed(anyLong(), anyString())).willReturn(false);
        given(memberStateReader.summarize(ROOM_MISSION_ID))
                .willReturn(MissionMemberStateSummary.empty());
        given(outcomeWriter.save(any(MissionOutcome.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        outcomeService.confirm(roomMission(), NOW);

        // then
        var inOrder = org.mockito.Mockito.inOrder(memberStateWriter, memberStateReader);
        inOrder.verify(memberStateWriter).expireUnresolved(ROOM_MISSION_ID, CLOSES_AT);
        inOrder.verify(memberStateReader).summarize(ROOM_MISSION_ID);
    }

    @Test
    @DisplayName("같은 집계 버전으로 이미 확정된 미션은 덮어쓰지 않고 스킵한다")
    void givenAlreadyConfirmed_whenConfirm_thenSkipsWithoutWriting() {
        // given
        given(outcomeReader.isConfirmed(ROOM_MISSION_ID, MissionOutcome.METRIC_SCHEMA_V1))
                .willReturn(true);

        // when
        Optional<MissionOutcome> confirmed = outcomeService.confirm(roomMission(), NOW);

        // then
        assertThat(confirmed).isEmpty();
        verifyNoInteractions(memberStateWriter, memberStateReader);
        verify(outcomeWriter, never()).save(any());
    }
}
