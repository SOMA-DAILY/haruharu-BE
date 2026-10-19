package org.son.haruharu.domain.cohort.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.cohort.business.command.CreateCohortCommand;
import org.son.haruharu.domain.cohort.business.command.UpdateCohortCommand;
import org.son.haruharu.domain.cohort.business.dto.CohortResponse;
import org.son.haruharu.domain.cohort.business.exception.CohortHasAssignedApplicationsException;
import org.son.haruharu.domain.cohort.business.exception.CohortOpenAlreadyExistsException;
import org.son.haruharu.domain.cohort.business.exception.CohortSequenceDuplicateException;
import org.son.haruharu.domain.cohort.business.exception.RecruitTrackInactiveException;
import org.son.haruharu.domain.cohort.implement.ApplicationCloseReason;
import org.son.haruharu.domain.cohort.implement.Cohort;
import org.son.haruharu.domain.cohort.implement.CohortApplication;
import org.son.haruharu.domain.cohort.implement.CohortApplicationReader;
import org.son.haruharu.domain.cohort.implement.CohortApplicationStatus;
import org.son.haruharu.domain.cohort.implement.CohortReader;
import org.son.haruharu.domain.cohort.implement.CohortStatus;
import org.son.haruharu.domain.cohort.implement.CohortWriter;
import org.son.haruharu.domain.cohort.implement.RecruitAudience;
import org.son.haruharu.domain.cohort.implement.RecruitTrack;
import org.son.haruharu.domain.cohort.implement.RecruitTrackReader;
import org.son.haruharu.domain.cohort.implement.exception.InvalidCohortException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CohortServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 18, 12, 0);
    private static final LocalDateTime OPENS = LocalDateTime.of(2026, 10, 20, 10, 0);
    private static final LocalDateTime CLOSES = LocalDateTime.of(2026, 10, 26, 0, 0);
    private static final LocalDateTime DEADLINE = LocalDateTime.of(2026, 10, 27, 12, 0);

    @Mock RecruitTrackReader trackReader;
    @Mock CohortReader cohortReader;
    @Mock CohortWriter cohortWriter;
    @Mock CohortApplicationReader applicationReader;
    @Mock CohortApplicationClosingService closingService;
    CohortService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        service = new CohortService(trackReader, cohortReader, cohortWriter, applicationReader, closingService, clock);
    }

    private RecruitTrack track(boolean active) {
        return new RecruitTrack(1L, "seoul-student", "서울 대학생", "서울", RecruitAudience.STUDENT, 20, 29,
                List.of(), 1, active, NOW, NOW);
    }

    private Cohort cohort(CohortStatus status) {
        return new Cohort(10L, 1L, 6, status, OPENS, CLOSES, DEADLINE, "10월 마지막 주", 6, 6, null, "admin:1",
                NOW, NOW);
    }

    private CreateCohortCommand createCommand() {
        return new CreateCohortCommand(1L, 6, OPENS, CLOSES, DEADLINE, "10월 마지막 주", 6, 6);
    }

    @Test
    @DisplayName("기수는 트랙을 잠근 뒤 초안으로 만들고 만든 운영진을 남긴다")
    void givenValidCommand_whenCreate_thenLocksTrackAndSavesDraft() {
        given(trackReader.getByIdForUpdate(1L)).willReturn(track(true));
        given(cohortReader.existsSequence(1L, 6)).willReturn(false);
        given(cohortWriter.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        CohortResponse response = service.create(createCommand(), "admin:7");

        assertThat(response.status()).isEqualTo(CohortStatus.DRAFT);
        assertThat(response.name()).isEqualTo("서울 대학생 6기");
        assertThat(response.createdBy()).isEqualTo("admin:7");
        InOrder order = inOrder(trackReader, cohortReader);
        order.verify(trackReader).getByIdForUpdate(1L);
        order.verify(cohortReader).existsSequence(1L, 6);
    }

    @Test
    @DisplayName("같은 트랙에 같은 번호의 기수가 있으면 409")
    void givenDuplicateSequence_whenCreate_thenConflict() {
        given(trackReader.getByIdForUpdate(1L)).willReturn(track(true));
        given(cohortReader.existsSequence(1L, 6)).willReturn(true);

        assertThatThrownBy(() -> service.create(createCommand(), "admin:7"))
                .isInstanceOf(CohortSequenceDuplicateException.class);
        verify(cohortWriter, never()).save(any());
    }

    @Test
    @DisplayName("입력이 잘못되면 트랙을 잠그기 전에 거절한다")
    void givenInvalidSchedule_whenCreate_thenRejectsBeforeLocking() {
        var command = new CreateCohortCommand(1L, 6, CLOSES, OPENS, DEADLINE, "10월 마지막 주", null, null);

        assertThatThrownBy(() -> service.create(command, "admin:7")).isInstanceOf(InvalidCohortException.class);
        verifyNoInteractions(trackReader);
    }

    @Test
    @DisplayName("공개는 트랙을 먼저 잠그고 기수를 잠근 뒤 OPEN 으로 바꾼다")
    void givenDraft_whenPublish_thenLocksTrackThenCohortAndOpens() {
        given(cohortReader.getTrackId(10L)).willReturn(1L);
        given(trackReader.getByIdForUpdate(1L)).willReturn(track(true));
        given(cohortReader.getByIdForUpdate(10L)).willReturn(cohort(CohortStatus.DRAFT));
        given(cohortReader.existsOpen(1L)).willReturn(false);
        given(cohortWriter.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        CohortResponse response = service.publish(10L);

        assertThat(response.status()).isEqualTo(CohortStatus.OPEN);
        assertThat(response.isAccepting()).isFalse(); // 신청 시작 전
        InOrder order = inOrder(trackReader, cohortReader);
        order.verify(trackReader).getByIdForUpdate(1L);
        order.verify(cohortReader).getByIdForUpdate(10L);
    }

    @Test
    @DisplayName("트랙에 이미 OPEN 기수가 있으면 공개를 409 로 거절한다")
    void givenAnotherOpenCohort_whenPublish_thenConflict() {
        given(cohortReader.getTrackId(10L)).willReturn(1L);
        given(trackReader.getByIdForUpdate(1L)).willReturn(track(true));
        given(cohortReader.getByIdForUpdate(10L)).willReturn(cohort(CohortStatus.DRAFT));
        given(cohortReader.existsOpen(1L)).willReturn(true);

        assertThatThrownBy(() -> service.publish(10L)).isInstanceOf(CohortOpenAlreadyExistsException.class);
        verify(cohortWriter, never()).save(any());
    }

    @Test
    @DisplayName("비활성 트랙의 기수는 공개하지 않는다")
    void givenInactiveTrack_whenPublish_thenConflict() {
        given(cohortReader.getTrackId(10L)).willReturn(1L);
        given(trackReader.getByIdForUpdate(1L)).willReturn(track(false));
        given(cohortReader.getByIdForUpdate(10L)).willReturn(cohort(CohortStatus.DRAFT));

        assertThatThrownBy(() -> service.publish(10L)).isInstanceOf(RecruitTrackInactiveException.class);
        verify(cohortWriter, never()).save(any());
    }

    @Test
    @DisplayName("공개 중인 기수를 취소하면 CANCELLED 가 된다")
    void givenOpenCohort_whenCancel_thenCancelled() {
        given(cohortReader.getTrackId(10L)).willReturn(1L);
        given(trackReader.getByIdForUpdate(1L)).willReturn(track(true));
        given(cohortReader.getByIdForUpdate(10L)).willReturn(cohort(CohortStatus.OPEN));
        given(cohortWriter.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.cancel(10L).status()).isEqualTo(CohortStatus.CANCELLED);
    }

    private CohortApplication application(Long id, Long userId) {
        return new CohortApplication(id, userId, 10L, 1L, CohortApplicationStatus.QUEUED, null, null, null, "한국대",
                "컴퓨터공학과", null, null, null, null, null, 0, NOW, NOW, null, null, null, null, null, null, null,
                null, NOW, NOW);
    }

    @Test
    @DisplayName("기수를 취소하면 배정 전 신청을 id 순으로 모두 미배정(COHORT_CANCELLED)으로 닫고 참가권을 돌려준다")
    void givenPendingApplications_whenCancel_thenClosesEachAsNotAssignedWithRefund() {
        given(cohortReader.getTrackId(10L)).willReturn(1L);
        given(trackReader.getByIdForUpdate(1L)).willReturn(track(true));
        given(cohortReader.getByIdForUpdate(10L)).willReturn(cohort(CohortStatus.OPEN));
        CohortApplication first = application(100L, 7L);
        CohortApplication second = application(101L, 8L);
        given(applicationReader.lockBeforeAssignment(10L)).willReturn(List.of(first, second));
        given(cohortWriter.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.cancel(10L).status()).isEqualTo(CohortStatus.CANCELLED);

        InOrder order = inOrder(cohortReader, applicationReader, closingService);
        order.verify(cohortReader).getByIdForUpdate(10L);
        order.verify(applicationReader).lockBeforeAssignment(10L);
        order.verify(closingService).closeBeforeAssignment(first, CohortApplicationStatus.NOT_ASSIGNED,
                ApplicationCloseReason.COHORT_CANCELLED, NOW);
        order.verify(closingService).closeBeforeAssignment(second, CohortApplicationStatus.NOT_ASSIGNED,
                ApplicationCloseReason.COHORT_CANCELLED, NOW);
    }

    @Test
    @DisplayName("방에 배정된 신청이 있으면 기수를 취소하지 않고 아무 신청도 닫지 않는다")
    void givenAssignedApplication_whenCancel_thenRejectsWithoutClosing() {
        given(cohortReader.getTrackId(10L)).willReturn(1L);
        given(trackReader.getByIdForUpdate(1L)).willReturn(track(true));
        given(cohortReader.getByIdForUpdate(10L)).willReturn(cohort(CohortStatus.OPEN));
        given(applicationReader.existsAssigned(10L)).willReturn(true);

        assertThatThrownBy(() -> service.cancel(10L)).isInstanceOf(CohortHasAssignedApplicationsException.class);
        verifyNoInteractions(closingService);
        verify(cohortWriter, never()).save(any());
    }

    @Test
    @DisplayName("초안의 번호를 이미 있는 번호로 바꾸면 409")
    void givenDuplicateSequence_whenUpdate_thenConflict() {
        given(cohortReader.getTrackId(10L)).willReturn(1L);
        given(trackReader.getByIdForUpdate(1L)).willReturn(track(true));
        given(cohortReader.getByIdForUpdate(10L)).willReturn(cohort(CohortStatus.DRAFT));
        given(cohortReader.existsSequence(1L, 7)).willReturn(true);

        assertThatThrownBy(() -> service.update(10L, new UpdateCohortCommand(7, null, null, null, null, null, null)))
                .isInstanceOf(CohortSequenceDuplicateException.class);
    }

    @Test
    @DisplayName("번호를 바꾸지 않는 수정은 번호 중복을 다시 보지 않는다")
    void givenScheduleOnlyUpdate_whenUpdate_thenSkipsSequenceCheck() {
        given(cohortReader.getTrackId(10L)).willReturn(1L);
        given(trackReader.getByIdForUpdate(1L)).willReturn(track(true));
        given(cohortReader.getByIdForUpdate(10L)).willReturn(cohort(CohortStatus.OPEN));
        given(cohortWriter.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        CohortResponse response = service.update(10L,
                new UpdateCohortCommand(6, null, null, null, "11월 첫째 주", null, 8));

        assertThat(response.periodLabel()).isEqualTo("11월 첫째 주");
        assertThat(response.targetFemale()).isEqualTo(8);
        verify(cohortReader, never()).existsSequence(any(), anyInt());
    }
}
