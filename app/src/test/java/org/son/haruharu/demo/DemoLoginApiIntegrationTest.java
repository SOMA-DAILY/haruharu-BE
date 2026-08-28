package org.son.haruharu.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.auth.implement.UserCredentialReader;
import org.son.haruharu.domain.demoaccess.implement.DemoAccessCode;
import org.son.haruharu.domain.demoaccess.storage.DemoAccessCodeEntity;
import org.son.haruharu.domain.demoaccess.storage.DemoAccessCodeJpaRepository;
import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.son.haruharu.domain.mission.implement.ActivationChannel;
import org.son.haruharu.domain.mission.implement.InteractionScope;
import org.son.haruharu.domain.mission.implement.Mission;
import org.son.haruharu.domain.mission.implement.MissionAttributeLevel;
import org.son.haruharu.domain.mission.implement.MissionGoal;
import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.PreferenceLeakRisk;
import org.son.haruharu.domain.mission.storage.MissionEntity;
import org.son.haruharu.domain.mission.storage.MissionJpaRepository;
import org.son.haruharu.domain.mission.storage.MissionRevisionEntity;
import org.son.haruharu.domain.mission.storage.MissionRevisionJpaRepository;
import org.son.haruharu.domain.mission.storage.RoomMissionJpaRepository;
import org.son.haruharu.domain.room.implement.MatchResult;
import org.son.haruharu.domain.room.implement.MatchResultReader;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.utils.JwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 데모 로그인 API end-to-end(#352 PR1). 어드민 코드 게이트 + JWT 발급 + 계정 lazy 생성을 실 HTTP로 검증한다.
 * 데모 계정은 설계상 영구 싱글턴(리셋은 방만 삭제)이라 테스트 간 클린 없이 멱등 재사용을 그대로 검증한다.
 */
@SpringBootTest(properties = "demo.feed.video-url=https://demo-bucket.s3.ap-northeast-2.amazonaws.com/demo/placeholder.mp4")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoLoginApiIntegrationTest {

    private static final String CODE = "HARU-DEMO-2026";

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper jsonMapper;
    @Autowired JwtProvider jwtProvider;
    @Autowired UserCredentialReader userCredentialReader;
    @Autowired DemoAccessCodeJpaRepository demoAccessCodeJpaRepository;
    @Autowired MissionJpaRepository missionJpaRepository;
    @Autowired MissionRevisionJpaRepository missionRevisionJpaRepository;
    @Autowired RoomMemberReader roomMemberReader;
    @Autowired RoomMissionJpaRepository roomMissionJpaRepository;
    @Autowired MatchResultReader matchResultReader;
    @Autowired ChatRoomReader chatRoomReader;

    @BeforeEach
    void setCode() {
        demoAccessCodeJpaRepository.save(DemoAccessCodeEntity.from(DemoAccessCode.of(CODE)));
        if (missionRevisionJpaRepository.findActiveCurrentRevisions().isEmpty()) {
            List.of("데모 미션 A", "데모 미션 B", "데모 미션 C", "데모 미션 D", "데모 미션 E", "데모 미션 F")
                    .forEach(this::persistMission);
        }
    }

    private Long userIdOf(MvcResult result) throws Exception {
        String accessToken = jsonMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("access_token").asString();
        return jwtProvider.extractUserId(accessToken);
    }

    @Test
    @DisplayName("올바른 코드로 데모 로그인하면 200·유효 토큰을 받고 DEMO 크레덴셜이 토큰 userId와 매핑된다")
    void givenValidCode_whenDemoLogin_thenTokensAndCredentialMapped() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/demo/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + CODE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").isNotEmpty())
                .andExpect(jsonPath("$.data.refresh_token").isNotEmpty())
                .andExpect(jsonPath("$.data.user_status").value("ACTIVE"))
                .andReturn();

        String accessToken = jsonMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("access_token").asString();
        assertThat(jwtProvider.isAccessToken(accessToken)).isTrue();

        Long demoUserId = userIdOf(result);
        assertThat(userCredentialReader.findByProviderTypeAndProviderId("DEMO", "haruharu-demo"))
                .isPresent()
                .get()
                .extracting("userId")
                .isEqualTo(demoUserId);

        // 로그인이 방을 세웠다: 데모+유령 6인, 그리고 RoomStartedEvent로 미션이 자동 배정됐다(이벤트 파이프라인 검증).
        Long roomId = roomMemberReader.findByUserId(demoUserId).get(0).roomId();
        assertThat(roomMemberReader.getByRoomId(roomId)).hasSize(6);
        assertThat(roomMemberReader.getByRoomId(roomId)).extracting(RoomMember::gender)
                .filteredOn("MALE"::equals).hasSize(3); // 3:3 구성
        assertThat(roomMissionJpaRepository.existsByRoomId(roomId)).isTrue();

        // 데모 토큰으로 피드를 열면 과거 슬롯에 심어진 영상이 보인다(데모도 그 슬롯을 올려 게이트 통과).
        mockMvc.perform(get("/api/v1/rooms/{id}/feed", roomId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.posts").isNotEmpty());

        // 매칭 즉시 성사 → 데모 멤버에 match_result가 생기고 DIRECT 1:1 채팅이 열린다.
        mockMvc.perform(post("/api/v1/demo/match-result")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + CODE + "\"}"))
                .andExpect(status().isOk());
        Long demoMemberId = roomMemberReader.getByRoomIdAndUserId(roomId, demoUserId).id();
        MatchResult match = matchResultReader.findByMember(demoMemberId).orElseThrow();
        assertThat(chatRoomReader.existsByMatchResultId(match.id())).isTrue();
    }

    @Test
    @DisplayName("틀린 코드로 데모 로그인하면 401 DEMO_ACCESS_DENIED")
    void givenWrongCode_whenDemoLogin_thenUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/demo/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"WRONG-CODE\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.error.code").value("DEMO_ACCESS_DENIED"));
    }

    private void persistMission(String description) {
        Mission mission = missionJpaRepository.save(MissionEntity.from(Mission.create())).toDomain();
        MissionRevision revision = MissionRevision.create(
                mission.id(), 1, description, MissionAttributeLevel.LOW, MissionGoal.OBSERVATION,
                ActivationChannel.VIDEO, InteractionScope.SELF,
                MissionAttributeLevel.LOW, MissionAttributeLevel.LOW,
                PreferenceLeakRisk.NONE, MissionAttributeLevel.LOW,
                "DEMO_" + mission.id(), new BigDecimal("0.8"), false);
        MissionRevision saved = missionRevisionJpaRepository.save(MissionRevisionEntity.from(revision)).toDomain();
        missionJpaRepository.save(MissionEntity.from(mission.activateRevision(saved.id())));
    }
}
