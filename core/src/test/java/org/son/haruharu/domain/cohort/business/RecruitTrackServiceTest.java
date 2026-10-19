package org.son.haruharu.domain.cohort.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.cohort.business.command.CreateRecruitTrackCommand;
import org.son.haruharu.domain.cohort.business.command.UpdateRecruitTrackCommand;
import org.son.haruharu.domain.cohort.business.dto.RecruitingTrackResponse;
import org.son.haruharu.domain.cohort.business.exception.RecruitTrackHasOpenCohortException;
import org.son.haruharu.domain.cohort.business.exception.RecruitTrackKeyDuplicateException;
import org.son.haruharu.domain.cohort.implement.Cohort;
import org.son.haruharu.domain.cohort.implement.CohortReader;
import org.son.haruharu.domain.cohort.implement.CohortStatus;
import org.son.haruharu.domain.cohort.implement.RecruitAudience;
import org.son.haruharu.domain.cohort.implement.RecruitTrack;
import org.son.haruharu.domain.cohort.implement.RecruitTrackReader;
import org.son.haruharu.domain.cohort.implement.RecruitTrackWriter;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecruitTrackServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 12, 0);

    @Mock RecruitTrackReader trackReader;
    @Mock RecruitTrackWriter trackWriter;
    @Mock CohortReader cohortReader;
    RecruitTrackService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        service = new RecruitTrackService(trackReader, trackWriter, cohortReader, clock);
    }

    private RecruitTrack track(Long id, String key, int displayOrder, boolean active) {
        return new RecruitTrack(id, key, key + " 이름", "서울", RecruitAudience.STUDENT, 20, 29, List.of(),
                displayOrder, active, NOW, NOW);
    }

    private Cohort open(Long id, Long trackId, LocalDateTime opensAt, LocalDateTime closesAt) {
        return new Cohort(id, trackId, 6, CohortStatus.OPEN, opensAt, closesAt, closesAt.plusDays(1), "10월 마지막 주",
                null, null, null, "admin:1", NOW, NOW);
    }

    @Test
    @DisplayName("이미 있는 키로 트랙을 만들면 409")
    void givenDuplicateKey_whenCreate_thenConflict() {
        given(trackReader.existsByKey("seoul-student")).willReturn(true);

        assertThatThrownBy(() -> service.create(new CreateRecruitTrackCommand("seoul-student", "서울 대학생", "서울",
                RecruitAudience.STUDENT, 20, 29, List.of(), 1)))
                .isInstanceOf(RecruitTrackKeyDuplicateException.class);
        verify(trackWriter, never()).save(any());
    }

    @Test
    @DisplayName("공개 중인 기수가 있는 트랙은 비활성화할 수 없다")
    void givenOpenCohort_whenDeactivate_thenConflict() {
        given(trackReader.getByIdForUpdate(1L)).willReturn(track(1L, "seoul-student", 1, true));
        given(cohortReader.existsOpen(1L)).willReturn(true);

        assertThatThrownBy(() -> service.update(1L,
                new UpdateRecruitTrackCommand(null, null, null, null, null, null, null, false)))
                .isInstanceOf(RecruitTrackHasOpenCohortException.class);
        verify(trackWriter, never()).save(any());
    }

    @Test
    @DisplayName("활성 상태를 건드리지 않는 수정은 열린 기수를 확인하지 않는다")
    void givenLabelOnly_whenUpdate_thenSaves() {
        given(trackReader.getByIdForUpdate(1L)).willReturn(track(1L, "seoul-student", 1, true));
        given(trackWriter.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.update(1L, new UpdateRecruitTrackCommand("서울 대학생(신촌)", null, null, null, null, null,
                null, null)).label()).isEqualTo("서울 대학생(신촌)");
        verify(cohortReader, never()).existsOpen(any());
    }

    @Test
    @DisplayName("앱 트랙 목록은 열린 기수가 있는 활성 트랙만 표시 순서대로 내리고 신청 가능 여부를 함께 준다")
    void givenTracksAndCohorts_whenGetRecruitingTracks_thenOnlyActiveWithOpenCohort() {
        Cohort accepting = open(100L, 2L, NOW.minusDays(1), NOW.plusDays(3));
        Cohort upcoming = open(101L, 1L, NOW.plusDays(1), NOW.plusDays(5));
        Cohort ofInactiveTrack = open(102L, 3L, NOW.minusDays(1), NOW.plusDays(3));
        given(cohortReader.findAllOpen()).willReturn(List.of(accepting, upcoming, ofInactiveTrack));
        given(trackReader.findAllByIdIn(any())).willReturn(List.of(
                track(2L, "gwangju-student", 2, true),
                track(1L, "seoul-student", 1, true),
                track(3L, "busan-worker", 0, false)));

        List<RecruitingTrackResponse> tracks = service.getRecruitingTracks().tracks();

        assertThat(tracks).extracting(RecruitingTrackResponse::key).containsExactly("seoul-student", "gwangju-student");
        assertThat(tracks.get(0).cohort().isAccepting()).isFalse();
        assertThat(tracks.get(1).cohort().isAccepting()).isTrue();
        assertThat(tracks.get(1).cohort().name()).isEqualTo("gwangju-student 이름 6기");
    }

    @Test
    @DisplayName("열린 기수가 없으면 빈 목록을 준다")
    void givenNoOpenCohort_whenGetRecruitingTracks_thenEmpty() {
        given(cohortReader.findAllOpen()).willReturn(List.of());
        given(trackReader.findAllByIdIn(List.of())).willReturn(List.of());

        assertThat(service.getRecruitingTracks().tracks()).isEmpty();
    }
}
