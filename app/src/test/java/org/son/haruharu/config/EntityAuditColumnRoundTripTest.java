package org.son.haruharu.config;

import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.block.implement.Block;
import org.son.haruharu.domain.block.storage.BlockEntity;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomType;
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
    @DisplayName("질문 및 질문권 Entity는 생성 시각을 왕복 보존한다")
    void givenQuestionAndGrant_whenRoundTrip_thenPreserveAllFields() {
        var question = org.son.haruharu.domain.question.implement.Question.create(1L,10L,11L,100L,101L,
                "달님","안녕",java.util.UUID.randomUUID(),CREATED_AT).answer("반가워",CREATED_AT.plusMinutes(1)).reportedBy(101L);
        assertThat(org.son.haruharu.domain.question.storage.QuestionEntity.from(question).toDomain()).isEqualTo(question);
        var grant = new org.son.haruharu.domain.question.implement.QuestionGrant(1L,1L,100L,true,CREATED_AT);
        assertThat(org.son.haruharu.domain.question.storage.QuestionGrantEntity.from(grant).toDomain()).isEqualTo(grant);
    }

    @Test
    void givenMissionAndAnnouncement_whenRoundTrip_thenPreserveAuditAndAllReferences() {
        var types = new org.son.haruharu.domain.mission.common.implement.MissionTypeRegistry(java.util.List.of(
                new org.son.haruharu.domain.mission.types.announcement.implement.AnnouncementMissionHandler(),
                new org.son.haruharu.domain.mission.types.profilereveal.implement.ProfileRevealMissionHandler(),
                new org.son.haruharu.domain.mission.types.recording.implement.RecordingMissionHandler()));
        var mission = new org.son.haruharu.domain.mission.common.implement.RoomMission(1L, 2L,
                org.son.haruharu.domain.mission.common.implement.MissionType.ANNOUNCEMENT,
                new org.son.haruharu.domain.mission.types.announcement.implement.AnnouncementMissionDefinition("안내"),
                new org.son.haruharu.domain.mission.common.implement.MissionImage(
                        "mission-images/3f2a1b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b-1080x720.jpg", 1080, 720),
                CREATED_AT, CREATED_AT.plusDays(1), CREATED_AT.plusHours(1), "admin:1", "admin:2", CREATED_AT, CREATED_AT.plusMinutes(1));
        assertThat(org.son.haruharu.domain.mission.common.storage.RoomMissionEntity.from(mission, types).toDomain(types)).isEqualTo(mission);
        var reveal = new org.son.haruharu.domain.mission.common.implement.RoomMission(3L, 2L,
                org.son.haruharu.domain.mission.common.implement.MissionType.PROFILE_REVEAL,
                org.son.haruharu.domain.mission.types.profilereveal.implement.ProfileRevealMissionDefinition.from(java.util.List.of("AGE", "MBTI")), null,
                CREATED_AT, null, null, "admin:1", "admin:1", CREATED_AT, CREATED_AT);
        assertThat(org.son.haruharu.domain.mission.common.storage.RoomMissionEntity.from(reveal, types).toDomain(types)).isEqualTo(reveal);
        var recording = new org.son.haruharu.domain.mission.common.implement.RoomMission(4L, 2L,
                org.son.haruharu.domain.mission.common.implement.MissionType.RECORDING,
                new org.son.haruharu.domain.mission.types.recording.implement.RecordingMissionDefinition("점심 인증"), null,
                CREATED_AT, null, null, "admin:1", "admin:1", CREATED_AT, CREATED_AT);
        assertThat(org.son.haruharu.domain.mission.common.storage.RoomMissionEntity.from(recording, types).toDomain(types)).isEqualTo(recording);
        var announcement = new org.son.haruharu.domain.mission.chat.implement.MissionAnnouncement(10L,1L,2L,
                org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementType.START,CREATED_AT,"게시 문구",true,"제목","본문",
                org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementStatus.SENT,null,50L,70L,"admin:1","admin:2",
                CREATED_AT.plusMinutes(2),CREATED_AT,CREATED_AT.plusMinutes(3));
        assertThat(org.son.haruharu.domain.mission.chat.storage.MissionAnnouncementEntity.from(announcement).toDomain()).isEqualTo(announcement);
    }

    @Test
    @DisplayName("모집 트랙·기수 Entity는 문항과 감사 시각을 포함해 모든 칸을 왕복 보존한다")
    void givenRecruitTrackAndCohort_whenRoundTrip_thenPreserveAllFields() {
        var track = new org.son.haruharu.domain.cohort.implement.RecruitTrack(1L, "seoul-student", "서울 대학생", "서울",
                org.son.haruharu.domain.cohort.implement.RecruitAudience.STUDENT, 20, 29, java.util.List.of(
                        new org.son.haruharu.domain.cohort.implement.TrackQuestion("grade", "학년",
                                org.son.haruharu.domain.cohort.implement.TrackQuestionType.CHOICE, true,
                                java.util.List.of("1학년", "2학년"))),
                1, true, CREATED_AT, CREATED_AT.plusMinutes(1));
        assertThat(org.son.haruharu.domain.cohort.storage.RecruitTrackEntity.from(track).toDomain()).isEqualTo(track);
        var cohort = new org.son.haruharu.domain.cohort.implement.Cohort(2L, 1L, 6,
                org.son.haruharu.domain.cohort.implement.CohortStatus.SETTLED, CREATED_AT, CREATED_AT.plusDays(5),
                CREATED_AT.plusDays(6), "10월 마지막 주", 6, null, CREATED_AT.plusDays(7), "admin:1",
                CREATED_AT, CREATED_AT.plusDays(7));
        assertThat(org.son.haruharu.domain.cohort.storage.CohortEntity.from(cohort).toDomain()).isEqualTo(cohort);
    }

    @Test
    @DisplayName("기수 신청 Entity는 답·설문·종료 기록과 감사 시각을 포함해 모든 칸을 왕복 보존한다")
    void givenCohortApplication_whenRoundTrip_thenPreserveAllFields() {
        var application = new org.son.haruharu.domain.cohort.implement.CohortApplication(1L, 7L, 3L, 2L,
                org.son.haruharu.domain.cohort.implement.CohortApplicationStatus.NOT_ASSIGNED,
                org.son.haruharu.domain.cohort.implement.ApplicantGender.FEMALE, "FEMALE",
                java.time.LocalDate.of(2003, 5, 1), "한국대", "컴퓨터공학과", Map.of("grade", "3학년"), "인스타그램",
                org.son.haruharu.domain.cohort.implement.FriendPreference.SAME_ROOM, "설레요", CREATED_AT.plusHours(1),
                1, CREATED_AT, CREATED_AT.plusHours(2),
                org.son.haruharu.domain.cohort.implement.ApplicationReviewReason.GENDER_MISMATCH, 9L,
                CREATED_AT.plusDays(1), "admin:1", CREATED_AT.plusDays(2),
                org.son.haruharu.domain.cohort.implement.ApplicationCloseReason.COHORT_CANCELLED,
                CREATED_AT.plusDays(2), "메모", CREATED_AT, CREATED_AT.plusDays(2));
        assertThat(org.son.haruharu.domain.cohort.storage.CohortApplicationEntity.from(application).toDomain())
                .isEqualTo(application);
    }

    @Test
    @DisplayName("프로필 Entity는 기수 신청 번호(phone)까지 왕복 보존한다")
    void givenProfileWithPhone_whenRoundTrip_thenPreservePhone() {
        var profile = new org.son.haruharu.domain.profile.implement.UserProfile(1L, 7L, "서울", "한국대", 175, "INFP",
                "닉", null, null, org.son.haruharu.domain.profile.implement.Gender.MALE, 2003, "컴퓨터공학과", "러닝",
                "01012345678");
        assertThat(org.son.haruharu.domain.profile.storage.UserProfileEntity.from(profile).toDomain())
                .isEqualTo(profile);
    }

    @Test
    @DisplayName("BlockEntity.from()은 createdAt을 복원한다")
    void givenBlockWithCreatedAt_whenFromThenToDomain_thenCreatedAtSurvives() {
        Block block = new Block(1L, 10L, 20L, CREATED_AT);

        Block restored = BlockEntity.from(block).toDomain();

        assertThat(restored.createdAt()).isEqualTo(CREATED_AT);
    }

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
        ChatRoom chatRoom = new ChatRoom(1L, ChatRoomType.GROUP, 10L, null, CREATED_AT).close(CREATED_AT.plusDays(3));

        ChatRoom restored = ChatRoomEntity.from(chatRoom).toDomain();

        assertThat(restored.createdAt()).isEqualTo(CREATED_AT);
        assertThat(restored.closeAt()).isEqualTo(chatRoom.closeAt());
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
