package org.son.haruharu.domain.home;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.matching.implement.MatchingQueue;
import org.son.haruharu.domain.matching.implement.MatchingQueueStatus;
import org.son.haruharu.domain.matching.storage.MatchingQueueEntity;
import org.son.haruharu.domain.matching.storage.MatchingQueueJpaRepository;
import org.son.haruharu.domain.room.storage.RoomJpaRepository;
import org.son.haruharu.domain.room.storage.RoomMemberJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HomeApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired RoomMemberJpaRepository roomMemberJpaRepository;
    @Autowired RoomJpaRepository roomJpaRepository;
    @Autowired MatchingQueueJpaRepository matchingQueueJpaRepository;
    @Autowired TransactionTemplate transactionTemplate;

    // "user 900은 어느 방에도 없다"는 전제를 스스로 보장한다(#166) — 같은 900을 쓰는
    // RoomToday/FinalSelection 통합 테스트가 실행 후 ACTIVE room을 남겨 active_room 단언이 깨졌다.
    // 큐도 매 테스트 초기화해 직전 신청 결과(last_apply_result) 단언이 다른 테스트에 오염되지 않게 한다.
    @BeforeEach
    void clean() {
        roomMemberJpaRepository.deleteAll();
        roomJpaRepository.deleteAll();
        matchingQueueJpaRepository.deleteAll();
    }

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    @DisplayName("홈 조회 응답이 point_balance·matching·active_room(snake_case)으로 직렬화된다")
    void givenNewUser_whenGetHome_thenReturnsAggregatedSnakeCase() throws Exception {
        mockMvc.perform(get("/api/v1/home").with(authentication(auth(900L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.point_balance").value(0))
                .andExpect(jsonPath("$.data.matching.status").value("NONE"))
                .andExpect(jsonPath("$.data.matching.confirm_at").exists())
                .andExpect(jsonPath("$.data.matching.queued_at").value(nullValue()))
                .andExpect(jsonPath("$.data.matching.last_apply_result").value(nullValue()))
                .andExpect(jsonPath("$.data.active_room").value(nullValue()));
    }

    @Test
    @DisplayName("직전 신청이 EXPIRED면 matching.last_apply_result에 outcome·expired_at·refund_amount가 내려간다")
    void givenExpiredQueue_whenGetHome_thenLastApplyResultExposed() throws Exception {
        // given: user 900이 신청(QUEUED) 후 22:00 배치에서 미매칭되어 EXPIRED로 전이·환불된 상태를
        //        프로덕션과 동일 경로(transitionStatus로 status=EXPIRED, updated_at=만료시각)로 재현한다.
        LocalDateTime queuedAt = LocalDateTime.of(2026, 7, 14, 12, 0);
        LocalDateTime expiredAt = LocalDateTime.of(2026, 7, 14, 22, 0);
        MatchingQueueEntity saved = matchingQueueJpaRepository.save(MatchingQueueEntity.from(
                MatchingQueue.join(900L, "MALE", LocalDate.of(1997, 5, 20), queuedAt)));
        Long queueId = saved.toDomain().id();
        // @Modifying 전이는 트랜잭션이 필요하다(프로덕션은 @Transactional 서비스가 감쌈).
        Integer updated = transactionTemplate.execute(s -> matchingQueueJpaRepository.transitionStatus(
                queueId, MatchingQueueStatus.QUEUED, MatchingQueueStatus.EXPIRED, expiredAt));
        org.assertj.core.api.Assertions.assertThat(updated).isEqualTo(1);

        // when & then
        mockMvc.perform(get("/api/v1/home").with(authentication(auth(900L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matching.status").value("NONE"))
                .andExpect(jsonPath("$.data.matching.last_apply_result.outcome").value("EXPIRED"))
                .andExpect(jsonPath("$.data.matching.last_apply_result.expired_at").value("2026-07-14T22:00:00"))
                .andExpect(jsonPath("$.data.matching.last_apply_result.refund_amount").value(20));
    }

    @Test
    @DisplayName("인증 없이 홈 조회 시 401을 반환한다")
    void givenNoAuth_whenGetHome_thenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/home"))
                .andExpect(status().isUnauthorized());
    }
}
