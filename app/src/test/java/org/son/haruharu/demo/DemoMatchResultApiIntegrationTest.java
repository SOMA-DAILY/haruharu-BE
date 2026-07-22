package org.son.haruharu.demo;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.chat.business.ChatService;
import org.son.haruharu.domain.profile.business.ProfileService;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 데모 최종선택 결과 + 채팅 시드(#266) end-to-end.
 * 앱스토어 리젝 사유가 "채팅의 pre-populated content"였으므로, 성사 후 <b>실제 chat API</b>로
 * 미리 채운 대화가 조회되고 리뷰어가 답장까지 보낼 수 있는지를 핵심으로 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoMatchResultApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserJpaRepository userJpaRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ChatService chatService;
    @Autowired ProfileService profileService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM chat_messages");
        jdbcTemplate.update("DELETE FROM chat_room_members");
        jdbcTemplate.update("DELETE FROM chat_rooms");
        jdbcTemplate.update("DELETE FROM match_results");
        jdbcTemplate.update("DELETE FROM coin_ledger");
        jdbcTemplate.update("DELETE FROM coin_wallet");
        jdbcTemplate.update("DELETE FROM video_posts");
        jdbcTemplate.update("DELETE FROM room_members");
        jdbcTemplate.update("DELETE FROM rooms");
        jdbcTemplate.update("DELETE FROM user_images");
        jdbcTemplate.update("DELETE FROM user_profiles");
        jdbcTemplate.update("DELETE FROM users");
    }

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private Long saveDemoUser() {
        Long userId = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null, true)))
                .toDomain().id();
        // 리뷰어 계정도 온보딩을 마친 실계정이므로 프로필이 있다 — 채팅 송신이 발신자 닉네임을 요구한다.
        profileService.seedDemoProfile(userId, "리뷰어", "서울", "https://picsum.photos/seed/haruharu-demo-reviewer/600/800");
        return userId;
    }

    private Long count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    private Long createDemoRoom(Long userId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/demo/room").with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("room_id").asLong();
    }

    private Long matchAndGetChatRoomId(Long userId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/demo/match-result")
                        .with(authentication(auth(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"MATCHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("MATCHED"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("chat_room_id").asLong();
    }

    @Test
    @DisplayName("MATCHED: 실제 채팅방·대화가 시드되고 리뷰어가 기존 chat API로 조회·답장할 수 있다")
    void givenDemoRoom_whenMatched_thenChatSeededAndReviewerCanReply() throws Exception {
        Long userId = saveDemoUser();
        createDemoRoom(userId);

        Long chatRoomId = matchAndGetChatRoomId(userId);
        assertThat(chatRoomId).isPositive();

        // 미리 채운 대화가 실제 chat_messages에 시드됐다
        Long seeded = count("SELECT count(*) FROM chat_messages WHERE chat_room_id = ?", chatRoomId);
        assertThat(seeded).isGreaterThanOrEqualTo(20L);
        // 방 참여자는 리뷰어 + 가상 상대 2명
        assertThat(count("SELECT count(*) FROM chat_room_members WHERE chat_room_id = ?", chatRoomId)).isEqualTo(2L);

        // 리뷰어가 기존 채팅 목록 API로 방을 본다
        mockMvc.perform(get("/api/v1/chat-rooms").with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chat_rooms.length()").value(1));

        // 리뷰어가 메시지 목록을 조회하면 미리 채운 대화가 보인다
        String messages = mockMvc.perform(get("/api/v1/chat-rooms/{id}/messages", chatRoomId)
                        .with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode items = objectMapper.readTree(messages).path("data").path("messages");
        assertThat(items.size()).isPositive();

        // 리뷰어가 답장을 보내면 정상 송신된다(리젝 사유의 "메시지 송신" 확인).
        // 전송은 REST가 아닌 STOMP 경로라, 그 핸들러가 호출하는 서비스 경로를 직접 검증한다
        // — 가상 상대가 탈퇴/비활성으로 판정되면 여기서 실패하므로 데모 상대 구성까지 함께 확인된다.
        long before = count("SELECT count(*) FROM chat_messages WHERE chat_room_id = ?", chatRoomId);
        chatService.sendMessage(chatRoomId, userId, "안녕하세요 반가워요!");
        assertThat(count("SELECT count(*) FROM chat_messages WHERE chat_room_id = ?", chatRoomId))
                .isEqualTo(before + 1);
    }

    @Test
    @DisplayName("MATCHED 재호출은 멱등 — 매칭 결과·채팅방이 중복 생성되지 않는다")
    void givenAlreadyMatched_whenMatchedAgain_thenIdempotent() throws Exception {
        Long userId = saveDemoUser();
        createDemoRoom(userId);

        Long first = matchAndGetChatRoomId(userId);
        Long second = matchAndGetChatRoomId(userId);

        assertThat(second).isEqualTo(first);
        assertThat(count("SELECT count(*) FROM match_results")).isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM chat_rooms")).isEqualTo(1L);
    }

    @Test
    @DisplayName("FAILED: 위로 구름이 지급되고 채팅방은 만들어지지 않는다")
    void givenDemoRoom_whenFailed_thenConsolationGrantedAndNoChat() throws Exception {
        Long userId = saveDemoUser();
        createDemoRoom(userId);

        mockMvc.perform(post("/api/v1/demo/match-result")
                        .with(authentication(auth(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"FAILED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("FAILED"))
                .andExpect(jsonPath("$.data.chat_room_id").doesNotExist());

        assertThat(count("SELECT count(*) FROM chat_rooms")).isZero();
        assertThat(count("SELECT count(*) FROM coin_ledger WHERE user_id = ? AND reason = 'MATCH_CONSOLATION'", userId))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("리셋하면 매칭 결과·채팅이 사라지고, 다시 성사시켜도 UNIQUE 충돌 없이 재시연된다")
    void givenMatched_whenReset_thenPurgedAndRematchable() throws Exception {
        Long userId = saveDemoUser();
        createDemoRoom(userId);
        matchAndGetChatRoomId(userId);

        mockMvc.perform(post("/api/v1/demo/reset").with(authentication(auth(userId))))
                .andExpect(status().isOk());

        // 하드삭제라 soft-delete 잔재 없이 완전히 사라진다(재생성 시 UNIQUE 충돌 방지)
        assertThat(count("SELECT count(*) FROM match_results")).isZero();
        assertThat(count("SELECT count(*) FROM chat_rooms")).isZero();
        assertThat(count("SELECT count(*) FROM chat_messages")).isZero();
        assertThat(count("SELECT count(*) FROM chat_room_members")).isZero();

        // 같은 멤버 쌍으로 다시 성사 — soft-delete였다면 여기서 UNIQUE 위반으로 깨진다
        Long chatRoomId = matchAndGetChatRoomId(userId);
        assertThat(chatRoomId).isPositive();
        assertThat(count("SELECT count(*) FROM match_results")).isEqualTo(1L);
    }
}
