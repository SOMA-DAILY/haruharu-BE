package org.son.haruharu.admin.domain.user;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.storage.ChatMessageEntity;
import org.son.haruharu.domain.chat.storage.ChatRoomEntity;
import org.son.haruharu.domain.room.implement.FinalSelection;
import org.son.haruharu.domain.room.implement.MatchResult;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.storage.FinalSelectionEntity;
import org.son.haruharu.domain.room.storage.MatchResultEntity;
import org.son.haruharu.domain.room.storage.RoomEntity;
import org.son.haruharu.domain.room.storage.RoomMemberEntity;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.storage.VideoPostEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 유저별 방 참여 이력(#508): 방 일정·가명·최종 선택·매칭·활동 수를 한 번에 조회한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Transactional
class AdminUserRoomHistoryApiIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;

    private final LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

    @Test
    @DisplayName("유저가 참여한 방마다 상태·일정·가명·최종 선택·받은 선택·결과·활동 수를 최신 입장순으로 준다")
    void givenRoomsWithSelectionsAndActivity_whenReadUserRooms_thenHistoryPerRoom() throws Exception {
        long userId = user();
        // 방 A(종료): 서로 골라 매칭 성사, 두 명에게 선택받음, 영상 2(삭제 1 제외)·그룹 채팅 3(DM 제외)
        long roomA = endedRoom(10);
        long meA = member(roomA, userId, "FEMALE", "하늘", now.minusDays(11));
        long partner = member(roomA, 201, "MALE", "바다", now.minusDays(11));
        long admirer = member(roomA, 202, "MALE", "산", now.minusDays(11));
        select(meA, partner);
        select(partner, meA);
        select(admirer, meA);
        em.persist(MatchResultEntity.from(MatchResult.of(Math.min(meA, partner), Math.max(meA, partner), now.minusDays(7))));
        video(meA, false);
        video(meA, false);
        video(meA, true);
        video(partner, false);
        long group = chatRoom(ChatRoom.createGroup(roomA));
        chat(group, userId, 3);
        chat(group, 201, 1);
        chat(chatRoom(ChatRoom.createDirect(999L)), userId, 2);
        // 방 B(종료): 내가 골랐지만 상대는 기권 → 한쪽만
        long roomB = endedRoom(6);
        long meB = member(roomB, userId, "FEMALE", "구름", now.minusDays(7));
        long other = member(roomB, 203, "MALE", null, now.minusDays(7));
        select(meB, other);
        select(other, null);
        // 방 C(시작 전): 결과 칸은 비운다
        long roomC = pendingRoom();
        member(roomC, userId, "FEMALE", null, now.minusHours(1));
        // 다른 유저만 있는 방은 섞이지 않는다
        member(endedRoom(5), 999, "MALE", "남", now.minusDays(6));
        em.flush();

        mvc.perform(get("/api/v1/admin/users/{userId}/rooms", userId).with(authentication(auth("OPERATOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].room_id").value(roomC))
                .andExpect(jsonPath("$.data[0].room_status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].outcome").isEmpty())
                .andExpect(jsonPath("$.data[0].pseudonym").isEmpty())
                .andExpect(jsonPath("$.data[1].room_id").value(roomB))
                .andExpect(jsonPath("$.data[1].outcome").value("ONE_WAY"))
                .andExpect(jsonPath("$.data[1].selected.user_id").value(203))
                .andExpect(jsonPath("$.data[1].selected.name").value("가명 없음"))
                .andExpect(jsonPath("$.data[1].received_count").value(0))
                .andExpect(jsonPath("$.data[2].room_id").value(roomA))
                .andExpect(jsonPath("$.data[2].room_status").value("ENDED"))
                .andExpect(jsonPath("$.data[2].total_days").value(3))
                .andExpect(jsonPath("$.data[2].starts_at").exists())
                .andExpect(jsonPath("$.data[2].ends_at").exists())
                .andExpect(jsonPath("$.data[2].pseudonym").value("하늘"))
                .andExpect(jsonPath("$.data[2].gender").value("FEMALE"))
                .andExpect(jsonPath("$.data[2].selection_submitted").value(true))
                .andExpect(jsonPath("$.data[2].selected.user_id").value(201))
                .andExpect(jsonPath("$.data[2].selected.name").value("바다"))
                .andExpect(jsonPath("$.data[2].received_count").value(2))
                .andExpect(jsonPath("$.data[2].outcome").value("MATCHED"))
                .andExpect(jsonPath("$.data[2].video_count").value(2))
                .andExpect(jsonPath("$.data[2].chat_count").value(3));
    }

    @Test
    @DisplayName("선택·활동이 담긴 방 이력은 SUPER·OPERATOR만 보고, 없는 유저는 404")
    void givenRoles_whenReadUserRooms_thenCsForbiddenAndMissingUser404() throws Exception {
        long userId = user();
        for (String role : List.of("SUPER", "OPERATOR", "CS")) {
            mvc.perform(get("/api/v1/admin/users/{userId}/rooms", userId).with(authentication(auth(role))))
                    .andExpect(role.equals("CS") ? status().isForbidden() : status().isOk());
        }
        mvc.perform(get("/api/v1/admin/users/{userId}/rooms", userId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/users/{userId}/rooms", 999999L).with(authentication(auth("OPERATOR"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ADMIN_USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("유저 상세의 참여 이력 상태는 저장값이 아니라 시각 파생 상태다")
    void givenStaleStoredStatus_whenReadUserDetail_thenDerivedStatus() throws Exception {
        long userId = user();
        long room = endedRoom(10); // 저장 status 는 PENDING 그대로(배치 지연)
        member(room, userId, "FEMALE", "하늘", now.minusDays(11));
        em.flush();

        mvc.perform(get("/api/v1/admin/users/{userId}", userId).with(authentication(auth("CS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participations[0].room_status").value("ENDED"));
    }

    private static UsernamePasswordAuthenticationToken auth(String role) {
        return new UsernamePasswordAuthenticationToken(1L, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private long user() {
        UserEntity e = UserEntity.from(new User(null, UserStatus.ACTIVE, null));
        em.persist(e);
        em.flush();
        return e.toDomain().id();
    }

    private long endedRoom(int startedDaysAgo) {
        LocalDateTime start = now.minusDays(startedDaysAgo);
        return room(Room.createScheduled(start, start.plusHours(60), start.plusHours(62), start.plusHours(63),
                start.plusDays(3), 3));
    }

    private long pendingRoom() {
        LocalDateTime start = now.plusDays(1);
        return room(Room.createScheduled(start, start.plusHours(60), start.plusHours(62), start.plusHours(63),
                start.plusDays(3), 3));
    }

    private long room(Room room) {
        RoomEntity e = RoomEntity.from(room);
        em.persist(e);
        em.flush();
        return e.toDomain().id();
    }

    private long member(long roomId, long userId, String gender, String pseudonym, LocalDateTime joinedAt) {
        RoomMember member = RoomMember.of(roomId, userId, gender, null, joinedAt);
        RoomMemberEntity e = RoomMemberEntity.from(pseudonym == null ? member : member.withPseudonym(pseudonym));
        em.persist(e);
        em.flush();
        return e.toDomain().id();
    }

    private void select(long memberId, Long selectedMemberId) {
        em.persist(FinalSelectionEntity.from(FinalSelection.of(memberId, selectedMemberId, now.minusDays(8))));
    }

    private void video(long memberId, boolean deleted) {
        LocalDateTime at = now.minusDays(9);
        em.persist(VideoPostEntity.from(new VideoPost(null, memberId, at, null, "오늘의 일상",
                "https://example.com/video.mp4", "https://example.com/thumb.jpg", false, null,
                deleted ? at.plusMinutes(1) : null, at)));
    }

    private long chatRoom(ChatRoom chatRoom) {
        ChatRoomEntity e = ChatRoomEntity.from(chatRoom);
        em.persist(e);
        em.flush();
        return e.toDomain().id();
    }

    private void chat(long chatRoomId, long senderId, int count) {
        for (int i = 0; i < count; i++) {
            em.persist(ChatMessageEntity.from(new ChatMessage(null, chatRoomId, senderId, "안녕 " + i, now.minusDays(9))));
        }
    }
}
