package org.son.haruharu.domain.mission.common.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
class MissionMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");
    Connection connection() throws SQLException { return DriverManager.getConnection(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword()); }
    void execute(String sql) throws Exception { try(var c=connection();var s=c.createStatement()){s.execute(sql);} }
    long count(String sql) throws Exception {try(var c=connection();var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
    String text(String sql) throws Exception {try(var c=connection();var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getString(1);}}
    void script(String name) throws Exception {
        Path root=Path.of("").toAbsolutePath();
        while(root!=null && !Files.isDirectory(root.resolve("scripts/sql"))) root=root.getParent();
        if(root==null)throw new IllegalStateException("Missing migration scripts");
        execute(Files.readString(root.resolve("scripts/sql/"+name)));
    }
    @BeforeEach
    void legacyDatabase() throws Exception {
        execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
        execute("""
            CREATE TABLE rooms(id bigint PRIMARY KEY);
            INSERT INTO rooms VALUES(1),(2);
            CREATE TABLE chat_rooms(id bigint PRIMARY KEY,type varchar(20),room_id bigint);
            INSERT INTO chat_rooms VALUES(10,'GROUP',1),(20,'GROUP',2);
            CREATE TABLE room_missions(id bigint PRIMARY KEY,content text);
            INSERT INTO room_missions VALUES(777,'과거 자동 미션 보존');
            CREATE TABLE chat_messages(id bigint PRIMARY KEY,chat_room_id bigint,sender_type varchar(20),sender_id bigint,
                content text,created_at timestamp NOT NULL,updated_at timestamp NOT NULL,deleted_at timestamp,
                reply_to_message_id bigint,reply_preview_content text,video_post_id bigint,
                CONSTRAINT ck_chat_messages_sender CHECK ((sender_type='SYSTEM' AND sender_id IS NULL) OR (sender_type='USER' AND sender_id IS NOT NULL)));
            CREATE TABLE chat_message_reactions(id bigint PRIMARY KEY,message_id bigint REFERENCES chat_messages(id),type text);
            """);
        script("457-room-schedule-admin-chat-up.sql");
        execute("""
            ALTER TABLE chat_messages ADD CONSTRAINT chat_messages_message_type_check CHECK(message_type IN ('TEXT','ADMIN'));
            ALTER TABLE chat_messages ADD CONSTRAINT ck_chat_messages_content_type CHECK(message_type='TEXT');
            ALTER TABLE chat_messages ADD CONSTRAINT uq_chat_messages_schedule_item UNIQUE(schedule_item_id);
            INSERT INTO room_schedule_items(id,room_id,run_at,message_content,reveal_fields,push,status,sent_message_id,
                created_by,updated_by,created_at,updated_at,sent_at,deleted_at)
            VALUES(100,1,'2026-10-07 10:00','과거 안내','MBTI',true,'SENT',500,'admin:1','admin:2','2026-10-01','2026-10-07','2026-10-07',null),
                  (101,1,'2026-10-08 10:00','예약 안내',null,false,'SCHEDULED',null,'admin:1','admin:1','2026-10-01','2026-10-01',null,null),
                  (102,1,'2026-10-07 11:00',null,'AGE',false,'FAILED',null,'admin:1','admin:1','2026-10-01','2026-10-01',null,null),
                  (103,1,'2026-10-07 12:00','취소 안내','DEPARTMENT',false,'CANCELED',null,'admin:1','admin:2','2026-10-01','2026-10-02',null,null),
                  (104,1,'2026-10-07 13:00','삭제 편성',null,false,'FAILED',null,'admin:1','admin:1','2026-10-01','2026-10-01',null,'2026-10-06');
            INSERT INTO chat_messages(id,chat_room_id,sender_type,sender_id,content,created_at,updated_at,message_type,schedule_item_id,admin_deleted_at,delete_broadcast_at)
            VALUES(500,10,'SYSTEM',null,'과거 안내','2026-10-07','2026-10-07','TEXT',100,'2026-10-07 12:00','2026-10-07 12:01'),
                  (501,10,'SYSTEM',null,'독립 공지','2026-10-07','2026-10-07','TEXT',null,null,null),
                  (502,10,'USER',55,'사용자 답장','2026-10-07','2026-10-07','TEXT',null,null,null);
            UPDATE chat_messages SET reply_to_message_id=500,reply_preview_content='과거 안내',video_post_id=12 WHERE id=502;
            INSERT INTO chat_message_reactions VALUES(1,500,'LAUGH');
            """);
    }

    @Test
    void givenLegacyRows_whenUpVerifyDown_thenPreservesMessagesAndOneToOneMapping() throws Exception {
        script("486-mission-preflight.sql");script("486-mission-up.sql");script("486-mission-verify.sql");
        assertThat(count("SELECT count(*) FROM room_mission_instances")).isEqualTo(5);
        assertThat(text("SELECT to_regclass('announcement_missions')")).isNull();
        // 공개 항목이 있던 편성은 공개 미션, 나머지는 안내형. 유형별 정의는 details jsonb 한 칸에 둔다.
        assertThat(text("SELECT string_agg(id||':'||type, ',' ORDER BY id) FROM room_mission_instances"))
                .isEqualTo("100:PROFILE_REVEAL,101:ANNOUNCEMENT,102:PROFILE_REVEAL,103:PROFILE_REVEAL,104:ANNOUNCEMENT");
        assertThat(text("SELECT details::text FROM room_mission_instances WHERE id=100")).isEqualTo("{\"reveal_fields\": [\"MBTI\"]}");
        assertThat(text("SELECT details::text FROM room_mission_instances WHERE id=101")).isEqualTo("{\"content\": \"예약 안내\"}");
        assertThat(text("SELECT data_type FROM information_schema.columns WHERE table_name='room_mission_instances' AND column_name='details'")).isEqualTo("jsonb");
        // 공개 미션의 채팅 문구는 안내 계획과 보낸 메시지에 남는다.
        assertThat(text("SELECT content FROM mission_announcements WHERE id=100")).isEqualTo("과거 안내");
        assertThat(text("SELECT content FROM chat_messages WHERE id=500")).isEqualTo("과거 안내");
        assertThat(count("SELECT count(*) FROM mission_announcements WHERE id=102 AND content IS NULL")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM information_schema.columns WHERE table_name='room_mission_instances' AND column_name IN ('content','reveal_fields')")).isZero();
        assertThat(count("SELECT count(*) FROM mission_announcements WHERE legacy_schedule_item_id=id AND room_mission_id=id")).isEqualTo(5);
        assertThat(text("SELECT message_type FROM chat_messages WHERE id=500")).isEqualTo("MISSION");
        assertThat(text("SELECT message_type FROM chat_messages WHERE id=501")).isEqualTo("TEXT");
        assertThat(count("SELECT count(*) FROM room_mission_instances WHERE id=103 AND canceled_at='2026-10-02'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM room_mission_instances WHERE id=104 AND deleted_at IS NOT NULL")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM chat_message_reactions WHERE message_id=500")).isEqualTo(1);
        assertThat(text("SELECT content FROM room_missions WHERE id=777")).isEqualTo("과거 자동 미션 보존");
        assertThatThrownBy(()->script("486-mission-up.sql")).isInstanceOf(SQLException.class);
        script("486-mission-down.sql");
        assertThat(text("SELECT message_type FROM chat_messages WHERE id=500")).isEqualTo("TEXT");
        assertThat(count("SELECT count(*) FROM chat_messages WHERE id=502 AND reply_to_message_id=500 AND video_post_id=12")).isEqualTo(1);
        assertThat(text("SELECT to_regclass('room_mission_instances')")).isNull();
    }

    @Test
    void givenMultipleRevealFields_whenUp_thenKeepsOrderAsJsonArray() throws Exception {
        execute("UPDATE room_schedule_items SET reveal_fields='MBTI,INTO_NOW,AGE' WHERE id=100");
        script("486-mission-preflight.sql");script("486-mission-up.sql");script("486-mission-verify.sql");
        assertThat(text("SELECT details::text FROM room_mission_instances WHERE id=100"))
                .isEqualTo("{\"reveal_fields\": [\"MBTI\", \"INTO_NOW\", \"AGE\"]}");
    }

    @Test
    void givenScheduleWithoutContentOrRevealFields_whenPreflightAndUp_thenAbort() throws Exception {
        execute("UPDATE room_schedule_items SET message_content='  ' WHERE id=101");
        assertThatThrownBy(()->script("486-mission-preflight.sql")).isInstanceOf(SQLException.class).hasMessageContaining("without content");
        assertThatThrownBy(()->script("486-mission-up.sql")).isInstanceOf(SQLException.class).hasMessageContaining("without content");
        assertThat(text("SELECT to_regclass('room_mission_instances')")).isNull();
    }

    @Test
    void givenMigratedSchema_whenInconsistentReferences_thenDatabaseRejects() throws Exception {
        script("486-mission-up.sql");
        assertThatThrownBy(()->execute("UPDATE chat_messages SET room_mission_id=101 WHERE id=500"))
                .isInstanceOf(SQLException.class).hasMessageContaining("fk_chat_messages_mission_announcement");
        assertThatThrownBy(()->execute("UPDATE mission_announcements SET room_id=2 WHERE id=100"))
                .isInstanceOf(SQLException.class).hasMessageContaining("fk_mission_announcement_room");
        assertThatThrownBy(()->execute("UPDATE chat_messages SET message_type='MISSION',room_mission_id=100,mission_announcement_id=100 WHERE id=501"))
                .isInstanceOf(SQLException.class).hasMessageContaining("uq_chat_messages_mission_announcement");
        assertThatThrownBy(()->execute("UPDATE chat_messages SET room_mission_id=100 WHERE id=502"))
                .isInstanceOf(SQLException.class).hasMessageContaining("ck_chat_messages_mission_reference");
        assertThat(count("INSERT INTO room_mission_instances(room_id,type,details,opens_at,created_at,updated_at) VALUES(1,'ANNOUNCEMENT','{\"content\":\"새 안내\"}',now(),now(),now()) RETURNING id")).isGreaterThan(104);
        assertThatThrownBy(()->script("486-mission-down.sql")).isInstanceOf(SQLException.class).hasMessageContaining("manual reconciliation");
        assertThat(count("SELECT count(*) FROM room_mission_instances")).isEqualTo(6);
    }

    @Test
    void givenMigratedSchema_whenInvalidTypeOrDetails_thenDatabaseRejects() throws Exception {
        script("486-mission-up.sql");
        assertThatThrownBy(() -> execute("UPDATE room_mission_instances SET details=NULL WHERE id=101"))
                .isInstanceOf(SQLException.class).hasMessageContaining("details");
        assertThatThrownBy(() -> execute("UPDATE room_mission_instances SET details='[\"MBTI\"]' WHERE id=101"))
                .isInstanceOf(SQLException.class).hasMessageContaining("ck_room_mission_instances_details");
        assertThatThrownBy(() -> execute("UPDATE room_mission_instances SET type='SELECTION' WHERE id=101"))
                .isInstanceOf(SQLException.class).hasMessageContaining("room_mission_instances_type_check");
    }

    @Test
    void givenOnlyDetailsChangedAfterCutover_whenRollback_thenRequiresReconciliation() throws Exception {
        script("486-mission-up.sql");
        execute("UPDATE room_mission_instances SET details='{\"content\":\"변경\"}' WHERE id=101");
        assertThatThrownBy(() -> script("486-mission-down.sql"))
                .isInstanceOf(SQLException.class).hasMessageContaining("manual reconciliation");
        assertThat(text("SELECT details->>'content' FROM room_mission_instances WHERE id=101")).isEqualTo("변경");
    }

    @Test
    void givenAmbiguousOrInflightLegacy_whenPreflightAndUp_thenAbortWithoutPartialTables() throws Exception {
        execute("UPDATE room_schedule_items SET status='SENDING' WHERE id=101");
        assertThatThrownBy(()->script("486-mission-preflight.sql")).isInstanceOf(SQLException.class).hasMessageContaining("in-flight");
        assertThatThrownBy(()->script("486-mission-up.sql")).isInstanceOf(SQLException.class);
        assertThat(text("SELECT to_regclass('room_mission_instances')")).isNull();
        execute("UPDATE room_schedule_items SET status='SCHEDULED' WHERE id=101; UPDATE chat_messages SET chat_room_id=20 WHERE id=500");
        assertThatThrownBy(()->script("486-mission-up.sql")).isInstanceOf(SQLException.class).hasMessageContaining("Unclassified");
        assertThat(text("SELECT to_regclass('chat_message_mission_backup_486')")).isNull();
    }

    @Test
    void givenSchemaWithoutRecordingType_whenApply496_thenAllowsRecordingAndIsRerunnable() throws Exception {
        script("486-mission-up.sql");
        // #496 이전에 이관했거나 Hibernate가 만든 DB: 두 유형만 허용하는 CHECK가 생성된 이름으로 걸려 있다.
        execute("""
            ALTER TABLE room_mission_instances DROP CONSTRAINT room_mission_instances_type_check;
            ALTER TABLE room_mission_instances ADD CONSTRAINT legacy_generated_type_check
                CHECK (type IN ('ANNOUNCEMENT','PROFILE_REVEAL'));
            """);
        String insert = "INSERT INTO room_mission_instances(room_id,type,details,opens_at,created_at,updated_at) "
                + "VALUES(1,'RECORDING','{\"content\":\"점심 인증\"}',now(),now(),now())";
        assertThatThrownBy(() -> execute(insert)).isInstanceOf(SQLException.class).hasMessageContaining("legacy_generated_type_check");

        script("496-mission-type-recording-up.sql");
        script("496-mission-type-recording-up.sql");
        execute(insert);
        assertThat(count("SELECT count(*) FROM room_mission_instances WHERE type='RECORDING'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM pg_constraint c JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='type' "
                + "WHERE c.conrelid='room_mission_instances'::regclass AND c.contype='c' AND a.attnum=ANY(c.conkey)")).isEqualTo(1);
        // 다른 제약과 기존 행은 그대로다.
        assertThat(count("SELECT count(*) FROM room_mission_instances WHERE type IN ('ANNOUNCEMENT','PROFILE_REVEAL')")).isEqualTo(5);
        assertThatThrownBy(() -> execute("UPDATE room_mission_instances SET type='SELECTION' WHERE id=101"))
                .isInstanceOf(SQLException.class).hasMessageContaining("room_mission_instances_type_check");
        assertThatThrownBy(() -> execute("UPDATE room_mission_instances SET details='[]' WHERE id=101"))
                .isInstanceOf(SQLException.class).hasMessageContaining("ck_room_mission_instances_details");

        // 촬영 미션이 있으면 되돌리지 않는다. 없앤 뒤에는 두 유형만 허용하던 상태로 돌아간다.
        assertThatThrownBy(() -> script("496-mission-type-recording-down.sql"))
                .isInstanceOf(SQLException.class).hasMessageContaining("manual reconciliation");
        execute("DELETE FROM room_mission_instances WHERE type='RECORDING'");
        script("496-mission-type-recording-down.sql");
        assertThatThrownBy(() -> execute(insert)).isInstanceOf(SQLException.class).hasMessageContaining("room_mission_instances_type_check");
    }

    @Test
    void givenFreshMigration_whenUp_thenRecordingTypeIsAllowedWithout496() throws Exception {
        script("486-mission-up.sql");
        execute("INSERT INTO room_mission_instances(room_id,type,details,opens_at,created_at,updated_at) "
                + "VALUES(1,'RECORDING','{\"content\":\"점심 인증\"}',now(),now(),now())");
        // 이미 새 목록인 DB에 496을 돌려도 결과가 같다.
        script("496-mission-type-recording-up.sql");
        assertThat(count("SELECT count(*) FROM room_mission_instances WHERE type='RECORDING'")).isEqualTo(1);
    }
}
