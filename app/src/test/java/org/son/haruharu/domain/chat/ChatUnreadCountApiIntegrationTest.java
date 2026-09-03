package org.son.haruharu.domain.chat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChatUnreadCountApiIntegrationTest {

    private static final Long ME = 100L;
    private static final Long OTHER = 200L;
    private static final LocalDateTime JOINED_AT = LocalDateTime.of(2026, 9, 2, 12, 0);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("TRUNCATE TABLE chat_messages, chat_room_members, chat_rooms RESTART IDENTITY CASCADE");
    }

    @Test
    @DisplayName("GROUP·DIRECT 안읽음 합계는 활성 membership의 입장 이후 타인 미삭제 메시지만 집계한다")
    void givenMixedGroupAndDirectMessages_whenGetUnreadCount_thenCountsOnlyActivePostJoinUnreadMessagesFromOthers()
            throws Exception {
        long groupRoomId = insertChatRoom("GROUP", 1001L);
        long groupMembershipId = insertMembership(groupRoomId, ME, JOINED_AT, null, null);
        long readThroughId = insertMessage(groupRoomId, OTHER, "read", JOINED_AT, null);
        insertMessage(groupRoomId, OTHER, "pre-join-higher-id", JOINED_AT.minusSeconds(1), null);
        insertMessage(groupRoomId, ME, "self", JOINED_AT.plusSeconds(1), null);
        insertMessage(groupRoomId, OTHER, "deleted", JOINED_AT.plusSeconds(2), JOINED_AT.plusMinutes(1));
        insertMessage(groupRoomId, OTHER, "group-unread", JOINED_AT.plusSeconds(3), null);
        jdbcTemplate.update("UPDATE chat_room_members SET last_read_message_id = ? WHERE id = ?",
                readThroughId, groupMembershipId);

        long directRoomId = insertChatRoom("DIRECT", 1002L);
        insertMembership(directRoomId, ME, JOINED_AT, null, null);
        insertMessage(directRoomId, OTHER, "direct-before", JOINED_AT.minusSeconds(1), null);
        insertMessage(directRoomId, OTHER, "direct-equal", JOINED_AT, null);
        insertMessage(directRoomId, OTHER, "direct-after", JOINED_AT.plusSeconds(1), null);
        insertMessage(directRoomId, ME, "direct-self", JOINED_AT.plusSeconds(2), null);

        long leftRoomId = insertChatRoom("GROUP", 1003L);
        insertMembership(leftRoomId, ME, JOINED_AT, JOINED_AT.plusHours(1), null);
        insertMessage(leftRoomId, OTHER, "left-membership", JOINED_AT.plusSeconds(1), null);

        mockMvc.perform(get("/api/v1/chat-rooms/unread-count")
                        .with(authentication(auth(ME))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.unread_count").value(3));
    }

    @Test
    @DisplayName("집계 대상 메시지가 없으면 200과 0을 반환한다")
    void givenNoEligibleMessages_whenGetUnreadCount_thenReturnsOkWithZero() throws Exception {
        mockMvc.perform(get("/api/v1/chat-rooms/unread-count")
                        .with(authentication(auth(ME))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.unread_count").value(0));
    }

    @Test
    @DisplayName("인증 없이 전체 unread API를 호출하면 401을 반환한다")
    void givenUnauthenticatedRequest_whenGetUnreadCount_thenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/chat-rooms/unread-count"))
                .andExpect(status().isUnauthorized());
    }

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private long insertChatRoom(String type, long sourceId) {
        String sql = type.equals("GROUP")
                ? """
                INSERT INTO chat_rooms(type, room_id, match_result_id, created_at, updated_at, deleted_at)
                VALUES ('GROUP', ?, NULL, ?, ?, NULL) RETURNING id
                """
                : """
                INSERT INTO chat_rooms(type, room_id, match_result_id, created_at, updated_at, deleted_at)
                VALUES ('DIRECT', NULL, ?, ?, ?, NULL) RETURNING id
                """;
        return jdbcTemplate.queryForObject(sql, Long.class, sourceId, JOINED_AT, JOINED_AT);
    }

    private long insertMembership(long chatRoomId, long userId, LocalDateTime joinedAt,
                                  LocalDateTime leftAt, Long lastReadMessageId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO chat_room_members(chat_room_id, user_id, joined_at, left_at, last_read_message_id)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, Long.class, chatRoomId, userId, joinedAt, leftAt, lastReadMessageId);
    }

    private long insertMessage(long chatRoomId, long senderId, String content,
                               LocalDateTime createdAt, LocalDateTime deletedAt) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO chat_messages(chat_room_id, sender_id, content, created_at, updated_at, deleted_at)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, chatRoomId, senderId, content, createdAt, createdAt, deletedAt);
    }
}
