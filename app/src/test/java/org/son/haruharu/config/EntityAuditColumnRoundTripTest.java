package org.son.haruharu.config;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.storage.ChatMessageEntity;
import org.son.haruharu.domain.chat.storage.ChatRoomEntity;
import org.son.haruharu.domain.notification.implement.NotificationHistory;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.notification.storage.NotificationHistoryEntity;
import org.son.haruharu.domain.report.implement.Report;
import org.son.haruharu.domain.report.implement.ReportReason;
import org.son.haruharu.domain.report.implement.ReportStatus;
import org.son.haruharu.domain.report.storage.ReportEntity;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 도메인 객체가 createdAt을 보유하는 엔티티는 from()에서도 그 값을 복원해 왕복(toDomain → from → toDomain)이
 * 맞아야 한다(#290 회귀 방지).
 *
 * <p>복원하지 않으면 호출부가 넘긴 createdAt이 조용히 버려진다. BaseEntity.createdAt은 @Column(updatable = false)라
 * DB 값은 안 깨지지만, save()가 돌려주는 객체의 createdAt이 null이 된다. ChatService.sendMessage가 정확히
 * 그 반환값의 createdAt을 STOMP 브로드캐스트 sentAt으로 내보내므로, 이 왕복이 깨지면 타임스탬프가 유실된다.
 *
 * <p>필드 존재 여부만으로는 "복원했는지"를 리플렉션으로 알 수 없어(바이트코드 분석이 필요) 대상 엔티티를 직접 나열한다.
 * 도메인 객체에 createdAt을 추가하는 엔티티가 생기면 여기에도 추가한다.
 */
class EntityAuditColumnRoundTripTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 6, 1, 12, 0);

    @Test
    @DisplayName("UserEntity.from()은 createdAt을 복원한다")
    void givenUserWithCreatedAt_whenFromThenToDomain_thenCreatedAtSurvives() {
        User user = new User(1L, UserStatus.ACTIVE, CREATED_AT);

        User restored = UserEntity.from(user).toDomain();

        assertThat(restored.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    @DisplayName("ChatRoomEntity.from()은 createdAt을 복원한다")
    void givenChatRoomWithCreatedAt_whenFromThenToDomain_thenCreatedAtSurvives() {
        ChatRoom chatRoom = new ChatRoom(1L, 10L, CREATED_AT);

        ChatRoom restored = ChatRoomEntity.from(chatRoom).toDomain();

        assertThat(restored.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    @DisplayName("ChatMessageEntity.from()은 createdAt을 복원한다 — 브로드캐스트 sentAt의 근거")
    void givenChatMessageWithCreatedAt_whenFromThenToDomain_thenCreatedAtSurvives() {
        ChatMessage message = new ChatMessage(1L, 10L, 100L, "안녕하세요", CREATED_AT);

        ChatMessage restored = ChatMessageEntity.from(message).toDomain();

        assertThat(restored.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    @DisplayName("NotificationHistoryEntity.from()은 createdAt을 복원한다")
    void givenNotificationHistoryWithCreatedAt_whenFromThenToDomain_thenCreatedAtSurvives() {
        NotificationHistory history = new NotificationHistory(
                1L, 100L, NotificationType.CHAT, "제목", "본문", Map.of(), false, CREATED_AT);

        NotificationHistory restored = NotificationHistoryEntity.from(history, "{}").toDomain(Map.of());

        assertThat(restored.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    @DisplayName("ReportEntity.from()은 createdAt을 복원한다")
    void givenReportWithCreatedAt_whenFromThenToDomain_thenCreatedAtSurvives() {
        Report report = new Report(
                1L, 100L, 200L, ReportReason.ABUSIVE_LANGUAGE, "사유", ReportStatus.RECEIVED, CREATED_AT);

        Report restored = ReportEntity.from(report).toDomain();

        assertThat(restored.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    @DisplayName("createdAt이 null인 신규 도메인 객체도 변환에서 터지지 않는다 — INSERT 경로는 @PrePersist가 채운다")
    void givenNewDomainWithoutCreatedAt_whenFrom_thenNoFailure() {
        User newUser = new User(null, UserStatus.ONBOARDING, null);
        ChatMessage newMessage = ChatMessage.create(10L, 100L, "신규");

        assertThat(UserEntity.from(newUser).toDomain().createdAt()).isNull();
        assertThat(ChatMessageEntity.from(newMessage).toDomain().createdAt()).isNull();
    }
}
