package org.son.haruharu.domain.chat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.chat.business.ChatService;
import org.son.haruharu.domain.chat.business.dto.ChatRoomsResponse;
import org.son.haruharu.domain.chat.business.dto.MessagePageResponse;
import org.son.haruharu.domain.chat.business.dto.ReadResponse;
import org.son.haruharu.domain.chat.implement.ChatMessageReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChatLateJoinPrivacyIntegrationTest {

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

    @Autowired ChatService chatService;
    @Autowired ChatMessageReader chatMessageReader;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("TRUNCATE TABLE chat_messages, chat_room_members, chat_rooms RESTART IDENTITY CASCADE");
    }

    @Test
    @DisplayName("입장 직전·동일·직후 메시지 중 동일과 직후만 history에 노출된다")
    void givenMessagesBeforeAtAndAfterJoin_whenGetMessages_thenReturnsOnlyAtAndAfterJoin() {
        long groupRoomId = insertChatRoom("GROUP", 901L);
        insertMembership(groupRoomId, ME, JOINED_AT, null, null);
        insertMessage(groupRoomId, OTHER, "before", JOINED_AT.minusNanos(1_000), null);
        long equalId = insertMessage(groupRoomId, OTHER, "equal", JOINED_AT, null);
        long afterId = insertMessage(groupRoomId, OTHER, "after", JOINED_AT.plusNanos(1_000), null);

        MessagePageResponse firstPage = chatService.getMessages(groupRoomId, ME, null, 30);
        MessagePageResponse cursorPage = chatService.getMessages(groupRoomId, ME, afterId, 30);

        assertThat(firstPage.messages()).extracting(MessagePageResponse.MessageItem::content)
                .containsExactly("after", "equal");
        assertThat(cursorPage.messages()).extracting(MessagePageResponse.MessageItem::messageId)
                .containsExactly(equalId);

        long directRoomId = insertChatRoom("DIRECT", 902L);
        insertMembership(directRoomId, ME, JOINED_AT, null, null);
        insertMessage(directRoomId, OTHER, "direct-before", JOINED_AT.minusSeconds(1), null);
        insertMessage(directRoomId, OTHER, "direct-equal", JOINED_AT, null);

        assertThat(chatMessageReader.findPage(directRoomId, JOINED_AT, null, 30))
                .extracting(message -> message.content())
                .containsExactly("direct-equal");
    }

    @Test
    @DisplayName("입장 전 메시지는 더 높은 ID여도 마지막 메시지와 방 unread에서 제외된다")
    void givenPreJoinAndPostJoinMessages_whenGetMyRooms_thenLastMessageAndUnreadIgnorePreJoinRows() {
        long groupRoomId = insertChatRoom("GROUP", 903L);
        insertMembership(groupRoomId, ME, JOINED_AT, null, null);
        insertMessage(groupRoomId, OTHER, "eligible-at-boundary", JOINED_AT, null);
        insertMessage(groupRoomId, OTHER, "higher-id-but-before", JOINED_AT.minusSeconds(1), null);

        ChatRoomsResponse response = chatService.getMyRooms(ME);

        assertThat(response.chatRooms()).hasSize(1);
        ChatRoomsResponse.ChatRoomSummary summary = response.chatRooms().getFirst();
        assertThat(summary.lastMessage()).isEqualTo("eligible-at-boundary");
        assertThat(summary.lastMessageAt()).isEqualTo(JOINED_AT);
        assertThat(summary.unreadCount()).isOne();
    }

    @Test
    @DisplayName("입장 전 메시지 ID가 read cursor보다 크더라도 읽음 응답 unread에서 제외된다")
    void givenPreJoinMessagesRemainAfterMarkRead_whenReadResponseCalculated_thenUnreadIgnoresThem() {
        long groupRoomId = insertChatRoom("GROUP", 904L);
        insertMembership(groupRoomId, ME, JOINED_AT, null, null);
        long lowerReadCursor = insertMessage(groupRoomId, ME, "already-read", JOINED_AT, null);
        insertMessage(groupRoomId, OTHER, "eligible-at-boundary", JOINED_AT, null);
        insertMessage(groupRoomId, OTHER, "higher-id-before", JOINED_AT.minusSeconds(1), null);

        ReadResponse response = chatService.markRead(groupRoomId, ME, lowerReadCursor);

        assertThat(response.unreadCount()).isOne();
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
