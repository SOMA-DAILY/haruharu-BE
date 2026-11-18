package org.son.haruharu.domain.cohort.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.son.haruharu.utils.JwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 기수 신청 비활성(#510). haruharu.cohort.enabled 기본 false 라 RecruitController 빈이 없어 ACTIVE 유저도 404. */
// 이 플래그 전용 컨텍스트는 재사용되지 않는다. 닫을 때 공유 H2 스키마를 지우지 않도록 DB도 분리한다(#564).
@SpringBootTest(properties = {
        "haruharu.cohort.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:recruit_disabled;MODE=PostgreSQL;DB_CLOSE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecruitDisabledIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserJpaRepository userJpaRepository;
    @Autowired JwtProvider jwtProvider;

    @Test
    @DisplayName("기수 신청 비활성 시 트랙 조회 엔드포인트가 없어 404")
    void givenCohortDisabled_whenGetTracks_thenNotFound() throws Exception {
        Long userId = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();

        mockMvc.perform(get("/api/v1/recruit/tracks")
                        .header("Authorization", "Bearer " + jwtProvider.generateAccessToken(userId)))
                .andExpect(status().isNotFound());
    }
}
