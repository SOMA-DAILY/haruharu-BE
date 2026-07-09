package org.son.haruharu.domain.report.business;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.report.business.command.CreateReportCommand;
import org.son.haruharu.domain.report.business.dto.ReportResponse;
import org.son.haruharu.domain.report.business.exception.CannotReportSelfException;
import org.son.haruharu.domain.report.business.exception.ReportAlreadyExistsException;
import org.son.haruharu.domain.report.implement.Report;
import org.son.haruharu.domain.report.implement.ReportReader;
import org.son.haruharu.domain.report.implement.ReportReason;
import org.son.haruharu.domain.report.implement.ReportStatus;
import org.son.haruharu.domain.report.implement.ReportWriter;
import org.son.haruharu.domain.user.implement.UserReader;
import org.son.haruharu.domain.user.storage.exception.UserNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock ReportReader reportReader;
    @Mock ReportWriter reportWriter;
    @Mock UserReader userReader;

    ReportService reportService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-09T13:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final Long reporterId = 1L;
    private final Long reportedId = 2L;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(reportReader, reportWriter, userReader, clock);
    }

    @Test
    @DisplayName("정상 신고 시 RECEIVED 상태로 저장되고 신고 id를 반환한다")
    void givenValidReport_whenReport_thenSavedAsReceived() {
        given(reportReader.hasReported(reporterId, reportedId)).willReturn(false);
        given(reportWriter.save(any())).willAnswer(inv -> {
            Report r = inv.getArgument(0);
            return new Report(100L, r.reporterUserId(), r.reportedUserId(), r.reason(), r.detail(),
                    r.status(), r.createdAt());
        });

        ReportResponse response = reportService.report(reporterId,
                new CreateReportCommand(reportedId, ReportReason.ABUSIVE_LANGUAGE, "욕설을 들었습니다"));

        assertThat(response.reportId()).isEqualTo(100L);
        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        then(reportWriter).should().save(captor.capture());
        Report saved = captor.getValue();
        assertThat(saved.reporterUserId()).isEqualTo(reporterId);
        assertThat(saved.reportedUserId()).isEqualTo(reportedId);
        assertThat(saved.reason()).isEqualTo(ReportReason.ABUSIVE_LANGUAGE);
        assertThat(saved.detail()).isEqualTo("욕설을 들었습니다");
        assertThat(saved.status()).isEqualTo(ReportStatus.RECEIVED);
    }

    @Test
    @DisplayName("상세 설명 없이(null)도 신고할 수 있다 — detail은 선택 입력")
    void givenNoDetail_whenReport_thenSaved() {
        given(reportReader.hasReported(reporterId, reportedId)).willReturn(false);
        given(reportWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        reportService.report(reporterId, new CreateReportCommand(reportedId, ReportReason.OTHER, null));

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        then(reportWriter).should().save(captor.capture());
        assertThat(captor.getValue().detail()).isNull();
    }

    @Test
    @DisplayName("자기 자신을 신고하면 CannotReportSelfException이 발생한다")
    void givenSelfTarget_whenReport_thenThrows() {
        assertThatThrownBy(() -> reportService.report(reporterId,
                new CreateReportCommand(reporterId, ReportReason.SPAM_AD, null)))
                .isInstanceOf(CannotReportSelfException.class);
        then(reportWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("같은 대상을 이미 신고했으면 ReportAlreadyExistsException이 발생한다(유저·대상당 1회)")
    void givenAlreadyReported_whenReport_thenThrows() {
        given(reportReader.hasReported(reporterId, reportedId)).willReturn(true);

        assertThatThrownBy(() -> reportService.report(reporterId,
                new CreateReportCommand(reportedId, ReportReason.FAKE_PROFILE, null)))
                .isInstanceOf(ReportAlreadyExistsException.class);
        then(reportWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 유저를 신고하면 UserNotFoundException이 발생한다")
    void givenUnknownTarget_whenReport_thenThrows() {
        given(userReader.getById(999L)).willThrow(new UserNotFoundException());

        assertThatThrownBy(() -> reportService.report(reporterId,
                new CreateReportCommand(999L, ReportReason.INAPPROPRIATE_MEDIA, null)))
                .isInstanceOf(UserNotFoundException.class);
        then(reportWriter).should(never()).save(any());
    }
}
