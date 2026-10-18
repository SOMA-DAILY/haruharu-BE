package org.son.haruharu.admin.domain.cohort;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.storage.ChatRoomEntity;
import org.son.haruharu.domain.cohort.implement.ApplicantGender;
import org.son.haruharu.domain.cohort.implement.CohortApplication;
import org.son.haruharu.domain.cohort.implement.CohortApplicationStatus;
import org.son.haruharu.domain.cohort.storage.CohortApplicationEntity;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.storage.UserProfileEntity;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomComposition;
import org.son.haruharu.domain.room.implement.RoomEntryPath;
import org.son.haruharu.domain.room.implement.RoomInvite;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.storage.RoomEntity;
import org.son.haruharu.domain.room.storage.RoomInviteEntity;
import org.son.haruharu.domain.room.storage.RoomMemberEntity;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminCohortAssignmentApiIntegrationTest {

    private static final long COHORT = 3L;
    private static final LocalDateTime NOW = LocalDateTime.now().withNano(0);

    @Autowired MockMvc mockMvc;
    @PersistenceContext EntityManager em;

    private ResultActions assign(long roomId, String role, Long... applicationIds) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/rooms/" + roomId + "/applications")
                .with(authentication(new UsernamePasswordAuthenticationToken(7L, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)))))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"application_ids\":" + List.of(applicationIds) + "}"));
    }

    /** 시작 전(PENDING) 방. started 면 진행 중 방. */
    private long room(Long cohortId, Integer male, Integer female, boolean started) {
        LocalDateTime startsAt = started ? NOW.minusHours(1) : NOW.plusDays(1);
        Room room = Room.createScheduled(startsAt, startsAt.plusDays(2), startsAt.plusDays(2).plusHours(2),
                startsAt.plusDays(2).plusHours(3), startsAt.plusDays(3), 3,
                new RoomComposition(cohortId, "서울 6기 1조", male, female));
        RoomEntity entity = RoomEntity.from(started ? room.started(startsAt) : room);
        em.persist(entity);
        return entity.toDomain().id();
    }

    private long application(long userId, CohortApplicationStatus status, ApplicantGender gender, String passGender,
                             long cohortId) {
        em.createNativeQuery("insert into users (id, status, created_at, updated_at) values (" + userId
                + ", 'ACTIVE', now(), now())").executeUpdate();
        CohortApplicationEntity entity = CohortApplicationEntity.from(new CohortApplication(null, userId, cohortId, 1L,
                status, gender, passGender, LocalDate.of(2003, 5, 1), "한국대", "컴퓨터공학과", null, null, null, null,
                null, 0, NOW.minusHours(5), NOW.minusHours(4), null, null, null, null, null, null, null, null, NOW, NOW));
        em.persist(entity);
        return entity.toDomain().id();
    }

    private long queued(long userId, ApplicantGender gender) {
        return application(userId, CohortApplicationStatus.QUEUED, gender, gender.name(), COHORT);
    }

    private List<RoomMember> members(long roomId) {
        em.flush();
        em.clear();
        return em.createQuery("select m from RoomMemberEntity m where m.roomId = :roomId order by m.id",
                RoomMemberEntity.class).setParameter("roomId", roomId).getResultList().stream()
                .map(RoomMemberEntity::toDomain).toList();
    }

    private CohortApplication load(long applicationId) {
        return em.find(CohortApplicationEntity.class, applicationId).toDomain();
    }

    @Test
    @DisplayName("편성 대기 신청자를 넣으면 PASS 성별·생년으로 방 멤버가 되고 신청은 방 배정이 된다(초대코드 정원과 따로)")
    void givenQueued_whenAssign_thenMembersAndAssigned() throws Exception {
        long roomId = room(COHORT, 3, 3, false);
        em.persist(RoomInviteEntity.from(RoomInvite.issue(roomId, "WEB00001", 1, null, "admin:1").incrementUsed()));
        em.persist(RoomMemberEntity.from(RoomMember.of(roomId, 800L, "FEMALE", null, NOW)));
        long female = queued(901L, ApplicantGender.FEMALE);
        long male = queued(902L, ApplicantGender.MALE);
        em.flush();

        assign(roomId, "OPERATOR", male, female)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].application_id").value(female));

        List<RoomMember> members = members(roomId);
        assertThat(members).hasSize(3);
        RoomMember assigned = members.get(1);
        assertThat(assigned.entryPath()).isEqualTo(RoomEntryPath.APPLICATION);
        assertThat(assigned.applicationId()).isEqualTo(female);
        assertThat(assigned.gender()).isEqualTo("FEMALE");
        assertThat(assigned.birthday()).isEqualTo(LocalDate.of(2003, 5, 1));
        assertThat(assigned.pseudonym()).isNotNull();
        CohortApplication done = load(female);
        assertThat(done.status()).isEqualTo(CohortApplicationStatus.ASSIGNED);
        assertThat(done.roomId()).isEqualTo(roomId);
        assertThat(done.assignedBy()).isEqualTo("admin:7");
        assertThat(done.assignedAt()).isNotNull();
    }

    @Test
    @DisplayName("진행 중 방에 넣으면 열린 그룹채팅에도 들어간다")
    void givenActiveRoomWithGroupChat_whenAssign_thenJoinsGroupChat() throws Exception {
        long roomId = room(COHORT, null, null, true);
        ChatRoomEntity group = ChatRoomEntity.from(ChatRoom.createGroup(roomId));
        em.persist(group);
        long id = queued(901L, ApplicantGender.FEMALE);
        em.flush();

        assign(roomId, "OPERATOR", id).andExpect(status().isOk());

        em.flush();
        assertThat(em.createQuery("select count(c) from ChatRoomMemberEntity c where c.chatRoomId = :chatId "
                        + "and c.userId = 901", Long.class).setParameter("chatId", group.toDomain().id())
                .getSingleResult()).isEqualTo(1L);
    }

    @Test
    @DisplayName("성별 정원을 넘으면 하나도 넣지 않는다")
    void givenOverCapacity_whenAssign_thenRejectAll() throws Exception {
        long roomId = room(COHORT, 3, 1, false);
        em.persist(RoomMemberEntity.from(RoomMember.of(roomId, 800L, "FEMALE", null, NOW)));
        long male = queued(902L, ApplicantGender.MALE);
        long female = queued(901L, ApplicantGender.FEMALE);
        em.flush();

        assign(roomId, "OPERATOR", male, female)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ROOM_GENDER_CAPACITY_EXCEEDED"));
        assertThat(members(roomId)).hasSize(1);
        assertThat(load(male).status()).isEqualTo(CohortApplicationStatus.QUEUED);
    }

    @Test
    @DisplayName("편성 대기 아님·PASS 성별 불일치·다른 기수·이미 멤버·탈퇴 유저면 전부 거절, 기수 없는 방·끝난 방은 방 거절")
    void givenInvalid_whenAssign_thenRejected() throws Exception {
        long roomId = room(COHORT, null, null, false);
        long ok = queued(900L, ApplicantGender.MALE);
        long pending = application(901L, CohortApplicationStatus.IDENTITY_PENDING, null, null, COHORT);
        long mismatch = application(902L, CohortApplicationStatus.QUEUED, ApplicantGender.FEMALE, "MALE", COHORT);
        long otherCohort = application(903L, CohortApplicationStatus.QUEUED, ApplicantGender.MALE, "MALE", 99L);
        long member = queued(904L, ApplicantGender.MALE);
        long withdrawn = queued(905L, ApplicantGender.MALE);
        em.createNativeQuery("update users set status = '" + UserStatus.DELETED + "' where id = 905").executeUpdate();
        em.persist(RoomMemberEntity.from(RoomMember.of(roomId, 904L, "MALE", null, NOW)));
        long noCohortRoom = room(null, null, null, false);
        em.flush();

        for (long bad : List.of(pending, mismatch, otherCohort, member, withdrawn)) {
            assign(roomId, "OPERATOR", ok, bad)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("COHORT_APPLICATION_NOT_ASSIGNABLE"));
        }
        assertThat(load(ok).status()).isEqualTo(CohortApplicationStatus.QUEUED);
        assign(noCohortRoom, "OPERATOR", ok).andExpect(jsonPath("$.error.code").value("ROOM_NOT_ASSIGNABLE"));
        assign(roomId, "OPERATOR", ok, 999999L).andExpect(status().isNotFound());
        assign(999999L, "OPERATOR", ok).andExpect(status().isNotFound());
        assign(roomId, "CS", ok).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/rooms/" + roomId + "/applications")
                        .with(authentication(new UsernamePasswordAuthenticationToken(7L, null,
                                List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")))))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"application_ids\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("방 목록은 기수로 거르고 방 이름·정원을 주며, 방 상세 참여자에 학교/학과가 나온다(#529)")
    void givenCohortRooms_whenListAndDetail_thenCohortFieldsAndSchool() throws Exception {
        long roomId = room(COHORT, 3, 3, false);
        room(null, null, null, false);
        em.persist(RoomMemberEntity.from(RoomMember.of(roomId, 901L, "FEMALE", null, NOW)));
        em.persist(UserProfileEntity.from(new UserProfile(null, 901L, null, "한국대", null, null, "닉", null, null, null,
                null, "컴퓨터공학과", null, null)));
        em.flush();
        var operator = authentication(new UsernamePasswordAuthenticationToken(7L, null,
                List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"))));

        mockMvc.perform(get("/api/v1/admin/rooms?cohortId=" + COHORT).with(operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.total_elements").value(1))
                .andExpect(jsonPath("$.data.content[0].room_id").value(roomId))
                .andExpect(jsonPath("$.data.content[0].cohort_id").value(COHORT))
                .andExpect(jsonPath("$.data.content[0].name").value("서울 6기 1조"))
                .andExpect(jsonPath("$.data.content[0].female_capacity").value(3))
                .andExpect(jsonPath("$.data.content[0].female_count").value(1));
        mockMvc.perform(get("/api/v1/admin/rooms/" + roomId).with(operator))
                .andExpect(jsonPath("$.data.members[0].school").value("한국대"))
                .andExpect(jsonPath("$.data.members[0].department").value("컴퓨터공학과"));
    }
}
