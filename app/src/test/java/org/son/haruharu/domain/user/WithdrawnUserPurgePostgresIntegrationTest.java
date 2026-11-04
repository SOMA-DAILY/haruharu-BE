package org.son.haruharu.domain.user;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatPhoto;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.storage.ChatMessageEntity;
import org.son.haruharu.domain.chat.storage.ChatRoomEntity;
import org.son.haruharu.domain.notification.implement.DeviceToken;
import org.son.haruharu.domain.notification.implement.NotificationHistory;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.notification.storage.DeviceTokenEntity;
import org.son.haruharu.domain.notification.storage.NotificationHistoryEntity;
import org.son.haruharu.domain.profile.implement.UserImage;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.storage.UserImageEntity;
import org.son.haruharu.domain.profile.storage.UserProfileEntity;
import org.son.haruharu.domain.question.implement.Question;
import org.son.haruharu.domain.question.storage.QuestionEntity;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.storage.RoomEntity;
import org.son.haruharu.domain.room.storage.RoomMemberEntity;
import org.son.haruharu.domain.user.business.WithdrawnUserPurgeService;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.value.implement.UserValueAnswer;
import org.son.haruharu.domain.value.storage.UserValueAnswerEntity;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.storage.VideoPostEntity;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doNothing;

/**
 * #555 탈퇴 90일 파기를 실제 PostgreSQL 에서 확인한다. 전역 소프트삭제가 걸린 테이블의 물리 삭제와
 * native UPDATE 는 H2 로 대신할 수 없다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WithdrawnUserPurgePostgresIntegrationTest {

    private static final String BASE = "https://media.test.haruharu/";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired WithdrawnUserPurgeService service;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @PersistenceContext EntityManager em;
    @MockitoBean S3StoragePort storage;

    /** 탈퇴 회원 1명과 상대 회원 1명, 방·영상·채팅·질문·알림을 심는다. 반환: [탈퇴자 id, 상대 id, 방 id]. */
    private long[] seedWithdrawnUser(int daysSinceWithdrawal) {
        LocalDateTime now = LocalDateTime.now();
        long[] ids = tx.execute(status -> {
            UserEntity withdrawn = UserEntity.from(new User(null, UserStatus.DELETED, null));
            UserEntity other = UserEntity.from(new User(null, UserStatus.ACTIVE, null));
            em.persist(withdrawn);
            em.persist(other);
            Long w = withdrawn.toDomain().id();
            Long o = other.toDomain().id();

            em.persist(UserProfileEntity.from(new UserProfile(null, w, "서울", "학생", 170, "INFP", "탈퇴닉", null, null, null)));
            em.persist(UserImageEntity.from(new UserImage(null, w, BASE + "users/" + w + "/profile/1.jpg", 0)));

            RoomEntity room = RoomEntity.from(Room.create(LocalDate.now(), 3).started(now));
            em.persist(room);
            Long roomId = room.toDomain().id();
            RoomMemberEntity wm = RoomMemberEntity.from(RoomMember.of(roomId, w, "FEMALE", LocalDate.of(2000, 1, 1), now));
            RoomMemberEntity om = RoomMemberEntity.from(RoomMember.of(roomId, o, "MALE", LocalDate.of(1999, 5, 5), now));
            em.persist(wm);
            em.persist(om);
            Long wmId = wm.toDomain().id();
            Long omId = om.toDomain().id();

            VideoPostEntity post = VideoPostEntity.from(VideoPost.create(wmId, now.minusHours(1), null, "캡션",
                    BASE + "users/" + w + "/videos/v1/published/viewer.mp4",
                    BASE + "users/" + w + "/videos/v1/published/thumbnail.jpg", now)
                    .withFeedVideoUrl(BASE + "users/" + w + "/videos/v1/published/feed.mp4"));
            em.persist(post);
            VideoPostEntity otherPost = VideoPostEntity.from(VideoPost.create(omId, now.minusHours(1), null, "상대",
                    BASE + "users/" + o + "/videos/v2/published/viewer.mp4", null, now));
            em.persist(otherPost);

            ChatRoomEntity chatRoom = ChatRoomEntity.from(ChatRoom.createGroup(roomId));
            em.persist(chatRoom);
            Long chatRoomId = chatRoom.toDomain().id();
            em.persist(ChatMessageEntity.from(ChatMessage.create(chatRoomId, w, "안녕하세요 저는 탈퇴할 사람")));
            em.persist(ChatMessageEntity.from(ChatMessage.photo(chatRoomId, w, UUID.randomUUID().toString(),
                    new ChatPhoto("chat-photos/" + roomId + "/w.jpg", 100, 100, "digest-w"))));
            em.persist(ChatMessageEntity.from(ChatMessage.photo(chatRoomId, o, UUID.randomUUID().toString(),
                    new ChatPhoto("chat-photos/" + roomId + "/o.jpg", 100, 100, "digest-o"))));

            em.persist(QuestionEntity.from(Question.create(roomId, omId, wmId, o, w, "탈퇴닉", "질문 내용",
                    UUID.randomUUID(), 1, now)));

            em.persist(UserValueAnswerEntity.from(new UserValueAnswer(null, w, 1L, "A")));
            em.persist(DeviceTokenEntity.from(DeviceToken.of(w, "fcm-token-" + w)));
            em.persist(NotificationHistoryEntity.from(NotificationHistory.of(w, NotificationType.ROOM_STARTED,
                    "제목", "본문", Map.of()), "{}"));
            em.flush();
            return new long[]{w, o, roomId};
        });
        // 탈퇴 때 프로필·사진은 소프트삭제됐고, DELETED 전이 시각은 updated_at.
        jdbc.update("INSERT INTO video_reactions (video_post_id, user_id, reaction_type, created_at) "
                + "SELECT v.id, ?, 'LIKE', now() FROM video_posts v JOIN room_members m ON m.id = v.room_member_id WHERE m.user_id = ?",
                ids[1], ids[0]);
        jdbc.update("UPDATE user_profiles SET deleted_at = now() WHERE user_id = ?", ids[0]);
        jdbc.update("UPDATE user_images SET deleted_at = now() WHERE user_id = ?", ids[0]);
        jdbc.update("UPDATE users SET updated_at = now() - make_interval(days => ?) WHERE id = ?", daysSinceWithdrawal, ids[0]);
        return ids;
    }

    private int count(String table, String where, Object... args) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where, Integer.class, args);
        return n == null ? 0 : n;
    }

    @Test
    @DisplayName("탈퇴 90일이 지난 회원은 사진·영상·프로필·기기 정보가 파기되고 대화 본문·참여 이력은 식별자 없이 남으며 재실행은 멱등이다")
    void givenWithdrawnOverNinetyDays_whenPurge_thenPersonalDataRemovedAndIdempotent() {
        given(storage.publicBaseUrl()).willReturn(BASE);
        doNothing().when(storage).deleteObjectsOrThrow(anyCollection());
        long[] ids = seedWithdrawnUser(91);
        long w = ids[0];
        long o = ids[1];
        long roomId = ids[2];

        assertThat(service.findDueUserIds(0L)).contains(w).doesNotContain(o);

        Map<String, Integer> counts = service.purge(w);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> keys = ArgumentCaptor.forClass(Collection.class);
        then(storage).should().deleteObjectsOrThrow(keys.capture());
        assertThat(keys.getValue()).containsExactlyInAnyOrder(
                "users/" + w + "/videos/v1/published/viewer.mp4",
                "users/" + w + "/videos/v1/published/thumbnail.jpg",
                "users/" + w + "/videos/v1/published/feed.mp4",
                "chat-photos/" + roomId + "/w.jpg");

        assertThat(counts).containsEntry("video_posts", 1).containsEntry("user_profiles", 1)
                .containsEntry("user_images", 1).containsEntry("chat_message_photos", 1)
                .containsEntry("anonymous_question_nicknames", 1).containsEntry("room_member_birthdays", 1)
                .containsEntry("device_tokens", 1).containsEntry("notification_history", 1)
                .containsEntry("video_reactions", 1).containsEntry("user_value_answers", 1);

        // 파기: 소프트삭제 행까지 물리 삭제.
        assertThat(count("user_profiles", "user_id = ?", w)).isZero();
        assertThat(count("user_images", "user_id = ?", w)).isZero();
        assertThat(count("video_posts", "room_member_id IN (SELECT id FROM room_members WHERE user_id = ?)", w)).isZero();
        assertThat(count("device_tokens", "user_id = ?", w)).isZero();
        assertThat(count("user_value_answers", "user_id = ?", w)).isZero();
        assertThat(count("video_reactions", "user_id = ?", o)).isZero();
        assertThat(count("notification_history", "user_id = ?", w)).isZero();
        assertThat(count("chat_messages", "sender_id = ? AND photo_key IS NOT NULL", w)).isZero();
        assertThat(count("chat_messages", "sender_id = ? AND message_type = 'TEXT' AND content = '삭제된 메시지' "
                + "AND admin_deleted_at IS NOT NULL AND delete_broadcast_at IS NOT NULL", w)).isEqualTo(1);
        // 가명처리: 본문·행은 남고 식별 스냅샷만 비움.
        assertThat(count("chat_messages", "sender_id = ? AND content = '안녕하세요 저는 탈퇴할 사람'", w)).isEqualTo(1);
        assertThat(count("anonymous_questions", "recipient_user_id = ? AND recipient_nickname = '' AND content = '질문 내용'", w))
                .isEqualTo(1);
        assertThat(count("room_members", "user_id = ? AND birthday IS NULL AND gender = 'FEMALE'", w)).isEqualTo(1);
        // 보존: users 행, 상대 회원의 데이터.
        assertThat(count("users", "id = ? AND status = 'DELETED' AND purged_at IS NOT NULL", w)).isEqualTo(1);
        assertThat(count("video_posts", "room_member_id IN (SELECT id FROM room_members WHERE user_id = ?)", o)).isEqualTo(1);
        assertThat(count("chat_messages", "sender_id = ? AND photo_key IS NOT NULL", o)).isEqualTo(1);
        assertThat(count("room_members", "user_id = ? AND birthday IS NOT NULL", o)).isEqualTo(1);

        // 멱등: 다음 실행 대상에서 빠진다.
        assertThat(service.findDueUserIds(0L)).doesNotContain(w);
        assertThat(service.findDueUserIds(w)).doesNotContain(w);
    }

    @Test
    @DisplayName("탈퇴 89일째 회원은 대상이 아니고 S3 삭제가 실패하면 DB 는 그대로 두어 다음 날 재시도한다")
    void givenNotYetDueOrS3Failure_whenPurge_thenUntouched() {
        given(storage.publicBaseUrl()).willReturn(BASE);
        long[] fresh = seedWithdrawnUser(89);
        assertThat(service.findDueUserIds(0L)).doesNotContain(fresh[0]);

        long[] due = seedWithdrawnUser(120);
        willThrow(new IllegalStateException("s3 down")).given(storage).deleteObjectsOrThrow(anyCollection());

        assertThatThrownBy(() -> service.purge(due[0])).isInstanceOf(IllegalStateException.class);

        assertThat(count("user_profiles", "user_id = ?", due[0])).isEqualTo(1);
        assertThat(count("video_posts", "room_member_id IN (SELECT id FROM room_members WHERE user_id = ?)", due[0]))
                .isEqualTo(1);
        assertThat(count("users", "id = ? AND purged_at IS NULL", due[0])).isEqualTo(1);
        assertThat(service.findDueUserIds(0L)).contains(due[0]);
    }
}
