package org.son.haruharu.domain.chat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.son.haruharu.domain.chat.business.ChatMessageViews;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.storage.ChatMessageEntity;
import org.son.haruharu.domain.chat.storage.ChatMessageJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class LegacyMissionMessagePostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ChatMessageJpaRepository messages;
    @Autowired ChatMessageViews views;
    @Autowired jakarta.persistence.EntityManager entityManager;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void givenStoredMissionMessage_whenReadHistoryAndReply_thenPreservesContentAndDeletion(boolean deleted) {
        // #474 적용 DB의 미션 컬럼은 남겨두고 SYSTEM 안내로 읽는다.
        jdbc.execute("ALTER TABLE chat_messages ADD COLUMN operator_mission_id BIGINT UNIQUE");
        jdbc.execute("ALTER TABLE chat_messages ADD COLUMN mission_room_id BIGINT");
        jdbc.execute("ALTER TABLE chat_messages ADD COLUMN mission_template_id VARCHAR(20)");
        jdbc.execute("ALTER TABLE chat_messages ADD COLUMN mission_title VARCHAR(40)");
        jdbc.execute("ALTER TABLE chat_messages ADD COLUMN mission_image_id BIGINT");
        ChatMessage original = messages.saveAndFlush(ChatMessageEntity.from(
                ChatMessage.system(482L, "과거 미션 안내", null))).toDomain();
        jdbc.update("""
                UPDATE chat_messages SET operator_mission_id=482, mission_room_id=482,
                    mission_template_id='PHOTO', mission_title='과거 제목', mission_image_id=482,
                    admin_deleted_at=? WHERE id=?
                """, deleted ? LocalDateTime.of(2026, 10, 1, 12, 0) : null, original.id());
        messages.saveAndFlush(ChatMessageEntity.from(ChatMessage.reply(482L, 2L, "답장", original, "하루")));
        entityManager.clear();

        var history = messages.findByChatRoomIdOrderByIdDesc(482L, PageRequest.of(0, 10)).stream()
                .map(ChatMessageEntity::toDomain).toList();
        var items = views.toItems(history, 2L);
        assertThat(items).hasSize(2);
        assertThat(items.get(1).content()).isEqualTo(deleted ? ChatMessage.DELETED_CONTENT : "과거 미션 안내");
        assertThat(items.get(0).replyTo().available()).isEqualTo(!deleted);
        assertThat(messages.findFirstByChatRoomIdOrderByIdDesc(482L).orElseThrow().toDomain().content())
                .isEqualTo("답장");
        assertThat(jdbc.queryForObject("SELECT mission_title FROM chat_messages WHERE id=?", String.class, original.id()))
                .isEqualTo("과거 제목");
        assertThat(jdbc.queryForObject("SELECT content FROM chat_messages WHERE id=?", String.class, original.id()))
                .isEqualTo("과거 미션 안내");
    }
}
