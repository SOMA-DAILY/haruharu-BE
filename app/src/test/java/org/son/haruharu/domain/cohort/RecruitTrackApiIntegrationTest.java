package org.son.haruharu.domain.cohort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.cohort.implement.Cohort;
import org.son.haruharu.domain.cohort.implement.CohortWriter;
import org.son.haruharu.domain.cohort.implement.RecruitAudience;
import org.son.haruharu.domain.cohort.implement.RecruitTrack;
import org.son.haruharu.domain.cohort.implement.RecruitTrackWriter;
import org.son.haruharu.domain.cohort.implement.TrackQuestion;
import org.son.haruharu.domain.cohort.implement.TrackQuestionType;
import org.son.haruharu.domain.cohort.storage.CohortJpaRepository;
import org.son.haruharu.domain.cohort.storage.RecruitTrackJpaRepository;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.son.haruharu.utils.JwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 앱 모집 트랙 조회(#510). 열린 기수가 있는 활성 트랙만, 문항과 신청 가능 여부를 함께 내린다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecruitTrackApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserJpaRepository userJpaRepository;
    @Autowired JwtProvider jwtProvider;
    @Autowired RecruitTrackWriter trackWriter;
    @Autowired CohortWriter cohortWriter;
    @Autowired RecruitTrackJpaRepository trackJpaRepository;
    @Autowired CohortJpaRepository cohortJpaRepository;

    private String token;

    @BeforeEach
    void setUp() {
        cohortJpaRepository.deleteAllInBatch();
        trackJpaRepository.deleteAllInBatch();
        userJpaRepository.deleteAll();
        Long userId = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        token = jwtProvider.generateAccessToken(userId);
    }

    private RecruitTrack track(String key, String label, int displayOrder, List<TrackQuestion> questions) {
        return trackWriter.save(RecruitTrack.create(key, label, "서울", RecruitAudience.STUDENT, 20, 29, questions,
                displayOrder));
    }

    private Cohort cohort(RecruitTrack track, int sequence, LocalDateTime opensAt, LocalDateTime closesAt, boolean open) {
        Cohort draft = Cohort.create(track.id(), sequence, opensAt, closesAt, closesAt.plusDays(1), "10월 마지막 주",
                6, 6, "admin:1");
        return cohortWriter.save(open ? draft.publish(LocalDateTime.now()) : draft);
    }

    @Test
    @DisplayName("열린 기수가 있는 활성 트랙만 표시 순서대로, 문항·기수 이름·신청 가능 여부와 함께 내린다")
    void givenTracks_whenGetTracks_thenOnlyRecruitingTracks() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        RecruitTrack seoul = track("seoul-student", "서울 대학생", 2, List.of(
                new TrackQuestion("grade", "학년", TrackQuestionType.CHOICE, true, List.of("1학년", "2학년"))));
        RecruitTrack gwangju = track("gwangju-student", "광주 대학생", 1, List.of());
        RecruitTrack draftOnly = track("busan-student", "부산 대학생", 0, List.of());
        cohort(seoul, 5, now.minusDays(20), now.minusDays(10), false);
        cohort(seoul, 6, now.minusDays(1), now.plusDays(3), true);
        cohort(gwangju, 2, now.plusDays(1), now.plusDays(4), true);
        cohort(draftOnly, 1, now.minusDays(1), now.plusDays(3), false);

        mockMvc.perform(get("/api/v1/recruit/tracks").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tracks.length()").value(2))
                .andExpect(jsonPath("$.data.tracks[0].key").value("gwangju-student"))
                .andExpect(jsonPath("$.data.tracks[0].cohort.name").value("광주 대학생 2기"))
                .andExpect(jsonPath("$.data.tracks[0].cohort.is_accepting").value(false))
                .andExpect(jsonPath("$.data.tracks[1].key").value("seoul-student"))
                .andExpect(jsonPath("$.data.tracks[1].min_age").value(20))
                .andExpect(jsonPath("$.data.tracks[1].cohort.sequence").value(6))
                .andExpect(jsonPath("$.data.tracks[1].cohort.period_label").value("10월 마지막 주"))
                .andExpect(jsonPath("$.data.tracks[1].cohort.is_accepting").value(true))
                .andExpect(jsonPath("$.data.tracks[1].questions[0].key").value("grade"))
                .andExpect(jsonPath("$.data.tracks[1].questions[0].options[1]").value("2학년"));
    }

    @Test
    @DisplayName("로그인하지 않으면 401")
    void givenNoToken_whenGetTracks_thenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/recruit/tracks")).andExpect(status().isUnauthorized());
    }
}
