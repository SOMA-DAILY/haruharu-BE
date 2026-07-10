package org.son.haruharu.admin.domain.room;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.storage.UserProfileEntity;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.storage.RoomEntity;
import org.son.haruharu.domain.room.storage.RoomMemberEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminRoomApiIntegrationTest {

    @Autowired MockMvc mockMvc;

    @PersistenceContext
    EntityManager em;

    private static UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(1L, null, List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
    }

    private Long persistRoom(Room room) {
        RoomEntity entity = RoomEntity.from(room);
        em.persist(entity);
        return entity.toDomain().id();
    }

    @Test
    @DisplayName("status=ACTIVE 필터 시 진행중 방만 페이지로 반환되고 성비·참여자 수가 담긴다")
    void rooms_filterByStatus_returnsActiveWithGender() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Long activeId = persistRoom(Room.create(LocalDate.now(), 3).started(now));
        persistRoom(Room.create(LocalDate.now(), 3));                         // PENDING (제외)
        persistRoom(Room.create(LocalDate.now(), 3).started(now).ended(now)); // ENDED (제외)
        em.persist(RoomMemberEntity.from(RoomMember.of(activeId, 901L, "FEMALE", LocalDate.of(2000, 1, 1), now)));
        em.persist(RoomMemberEntity.from(RoomMember.of(activeId, 902L, "MALE", LocalDate.of(2000, 1, 1), now)));
        em.flush();

        mockMvc.perform(get("/api/v1/admin/rooms")
                        .param("status", "ACTIVE").param("page", "0").param("size", "20")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].room_id").value(activeId))
                .andExpect(jsonPath("$.data.content[0].member_count").value(2))
                .andExpect(jsonPath("$.data.content[0].female_count").value(1))
                .andExpect(jsonPath("$.data.content[0].male_count").value(1))
                .andExpect(jsonPath("$.data.page.total_elements").value(1));
    }

    @Test
    @DisplayName("방 상세는 방 정보 + 참여자 목록(닉네임·지역·나이 enrich)을 반환한다")
    void roomDetail_returnsMembersWithProfile() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Long roomId = persistRoom(Room.create(LocalDate.now(), 3).started(now));
        em.persist(RoomMemberEntity.from(RoomMember.of(roomId, 901L, "FEMALE", LocalDate.of(2000, 1, 1), now)));
        em.persist(UserProfileEntity.from(new UserProfile(null, 901L, "서울", "직장인", 168, "INFP", "여울", null, null)));
        em.flush();

        mockMvc.perform(get("/api/v1/admin/rooms/{roomId}", roomId).with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.room_id").value(roomId))
                .andExpect(jsonPath("$.data.members.length()").value(1))
                .andExpect(jsonPath("$.data.members[0].user_id").value(901))
                .andExpect(jsonPath("$.data.members[0].nickname").value("여울"))
                .andExpect(jsonPath("$.data.members[0].region").value("서울"))
                .andExpect(jsonPath("$.data.members[0].age").isNumber());
    }

    @Test
    @DisplayName("없는 방 상세 조회 시 404")
    void roomDetail_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/admin/rooms/{roomId}", 999999L).with(authentication(adminAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ADMIN_ROOM_NOT_FOUND"));
    }

    @Test
    @DisplayName("인증 없이 방 목록 접근 시 401")
    void rooms_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/rooms"))
                .andExpect(status().isUnauthorized());
    }
}
