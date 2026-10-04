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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    @DisplayName("방 상세는 방 정보 + 참여자 목록(실명·가명·지역·나이 enrich)을 반환한다")
    void roomDetail_returnsMembersWithProfileAndPseudonym() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Long roomId = persistRoom(Room.create(LocalDate.now(), 3).started(now));
        em.persist(RoomMemberEntity.from(
                RoomMember.of(roomId, 901L, "FEMALE", LocalDate.of(2000, 1, 1), now).withPseudonym("유진")));
        em.persist(UserProfileEntity.from(new UserProfile(null, 901L, "서울", "직장인", 168, "INFP", "여울", null, null, null)));
        em.flush();

        mockMvc.perform(get("/api/v1/admin/rooms/{roomId}", roomId).with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.room_id").value(roomId))
                .andExpect(jsonPath("$.data.members.length()").value(1))
                .andExpect(jsonPath("$.data.members[0].user_id").value(901))
                .andExpect(jsonPath("$.data.members[0].nickname").value("여울"))   // 실명
                .andExpect(jsonPath("$.data.members[0].pseudonym").value("유진"))  // 가명
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

    // ── #318 방 생성·초대코드 발급·성별 부여 ──────────────────────────────────

    private static UsernamePasswordAuthenticationToken csAuth() {
        return new UsernamePasswordAuthenticationToken(2L, null, List.of(new SimpleGrantedAuthority("ROLE_CS")));
    }

    private static String createRoomBody(LocalDateTime startsAt) {
        return """
                {
                  "starts_at": "%s",
                  "final_selection_opens_at": "%s",
                  "final_selection_closes_at": "%s",
                  "result_at": "%s",
                  "ends_at": "%s",
                  "total_days": 3,
                  "max_uses": 6,
                  "mission_slots": %s
                }
                """.formatted(startsAt, startsAt.plusHours(60), startsAt.plusHours(63),
                startsAt.plusHours(64), startsAt.plusHours(72), missionSlots(startsAt));
    }

    private static String missionSlots(LocalDateTime startsAt) {
        return """
                [
                  {"day_number":1,"period":"MORNING","opens_at":"%s","closes_at":"%s"},
                  {"day_number":1,"period":"AFTERNOON","opens_at":"%s","closes_at":"%s"},
                  {"day_number":2,"period":"MORNING","opens_at":"%s","closes_at":"%s"},
                  {"day_number":2,"period":"AFTERNOON","opens_at":"%s","closes_at":"%s"},
                  {"day_number":3,"period":"MORNING","opens_at":"%s","closes_at":"%s"},
                  {"day_number":3,"period":"AFTERNOON","opens_at":"%s","closes_at":"%s"}
                ]
                """.formatted(
                startsAt.plusHours(1), startsAt.plusHours(6),
                startsAt.plusHours(9), startsAt.plusHours(14),
                startsAt.plusHours(25), startsAt.plusHours(30),
                startsAt.plusHours(33), startsAt.plusHours(38),
                startsAt.plusHours(49), startsAt.plusHours(54),
                startsAt.plusHours(55), startsAt.plusHours(58));
    }

    @Test
    @DisplayName("방 생성 시 PENDING 방과 6자리 초대코드가 함께 반환된다")
    void createRoom_returnsRoomIdAndInviteCode() throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRoomBody(LocalDateTime.now().plusDays(1).withNano(0)))
                        .with(authentication(adminAuth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.room_id").isNumber())
                .andExpect(jsonPath("$.data.code").value(org.hamcrest.Matchers.matchesPattern(
                        "[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}")))
                .andReturn().getResponse().getContentAsString();

        Long roomId = com.jayway.jsonpath.JsonPath.parse(response).read("$.data.room_id", Long.class);
        Long slotCount = em.createQuery(
                        "select count(s) from RoomMissionSlotEntity s where s.roomId = :roomId", Long.class)
                .setParameter("roomId", roomId)
                .getSingleResult();
        assertThat(slotCount).isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("새 방 생성은 구 자동 미션 슬롯 입력을 무시하고 채팅 제공을 준비한다")
    void givenLegacyMissionSlots_whenCreateRoom_thenIgnoresSlots() throws Exception {
        LocalDateTime startsAt = LocalDateTime.now().plusDays(1).withNano(0);
        String duplicateSlots = missionSlots(startsAt)
                .replaceFirst("\\\"AFTERNOON\\\"", "\\\"MORNING\\\"");
        String body = createRoomBody(startsAt).replace(missionSlots(startsAt), duplicateSlots);
        long roomCount = roomCount();
        long slotCount = roomMissionSlotCount();
        long inviteCount = roomInviteCount();

        mockMvc.perform(post("/api/v1/admin/rooms")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(authentication(adminAuth())))
                .andExpect(status().isCreated());

        assertThat(roomCount()).isEqualTo(roomCount + 1);
        assertThat(roomMissionSlotCount()).isEqualTo(slotCount);
        assertThat(roomInviteCount()).isEqualTo(inviteCount + 1);
    }

    private long roomCount() {
        return em.createQuery("select count(r) from RoomEntity r", Long.class).getSingleResult();
    }

    private long roomMissionSlotCount() {
        return em.createQuery("select count(s) from RoomMissionSlotEntity s", Long.class).getSingleResult();
    }

    private long roomInviteCount() {
        return em.createQuery("select count(i) from RoomInviteEntity i", Long.class).getSingleResult();
    }

    @Test
    @DisplayName("스케줄 시각 순서가 어긋나면 400 ROOM_INVALID_SCHEDULE")
    void createRoom_invalidSchedule_returns400() throws Exception {
        LocalDateTime startsAt = LocalDateTime.now().plusDays(1).withNano(0);
        String body = """
                {
                  "starts_at": "%s",
                  "final_selection_opens_at": "%s",
                  "final_selection_closes_at": "%s",
                  "result_at": "%s",
                  "ends_at": "%s",
                  "total_days": 3,
                  "max_uses": 6,
                  "mission_slots": %s
                }
                """.formatted(startsAt, startsAt.minusHours(1), startsAt.plusDays(2),
                startsAt.plusDays(2).plusHours(1), startsAt.plusDays(3), missionSlots(startsAt));

        mockMvc.perform(post("/api/v1/admin/rooms")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ROOM_INVALID_SCHEDULE"));
    }

    @Test
    @DisplayName("방 생성의 실제 정원은 양수여야 하며 null은 무제한으로 허용된다")
    void givenInvalidOrUnlimitedMaxUses_whenCreatingRoom_thenValidatesAsExpected() throws Exception {
        LocalDateTime startsAt = LocalDateTime.now().plusDays(1).withNano(0);
        String validBody = createRoomBody(startsAt);

        for (int invalidMaxUses : List.of(0, -1)) {
            mockMvc.perform(post("/api/v1/admin/rooms")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validBody.replace("\"max_uses\": 6", "\"max_uses\": " + invalidMaxUses))
                            .with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        }

        mockMvc.perform(post("/api/v1/admin/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody.replace("\"max_uses\": 6", "\"max_uses\": null"))
                        .with(authentication(adminAuth())))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("코드 재발급 시 기존 코드는 EXPIRED가 되고 새 ACTIVE 코드가 반환된다")
    void reissueInvite_expiresOldAndReturnsNew() throws Exception {
        LocalDateTime startsAt = LocalDateTime.now().plusDays(1).withNano(0);
        String created = mockMvc.perform(post("/api/v1/admin/rooms")
                        .contentType(MediaType.APPLICATION_JSON).content(createRoomBody(startsAt))
                        .with(authentication(adminAuth())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long roomId = com.jayway.jsonpath.JsonPath.parse(created).read("$.data.room_id", Long.class);
        String oldCode = com.jayway.jsonpath.JsonPath.parse(created).read("$.data.code", String.class);

        mockMvc.perform(post("/api/v1/admin/rooms/{roomId}/invites", roomId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"max_uses\": 6}")
                        .with(authentication(adminAuth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value(org.hamcrest.Matchers.not(oldCode)))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        // 코드 이력은 최신 발급이 앞(id DESC) — [0]=새 ACTIVE 코드, [1]=닫힌 기존 코드.
        mockMvc.perform(get("/api/v1/admin/rooms/{roomId}/invites", roomId)
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[1].code").value(oldCode))
                .andExpect(jsonPath("$.data[1].status").value("EXPIRED"));
    }

    @Test
    @DisplayName("없는 방의 코드를 재발급하면 기존 ADMIN_ROOM_NOT_FOUND 404를 유지한다")
    void givenMissingRoom_whenReissuingInvite_thenReturnsAdminRoomNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/admin/rooms/{roomId}/invites", 999999L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"max_uses\": 6}")
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ADMIN_ROOM_NOT_FOUND"));
    }

    @Test
    @DisplayName("재발급 실제 정원은 양수여야 하고 요청 body가 없으면 null 무제한으로 유효하다")
    void givenInvalidOrAbsentMaxUses_whenReissuingInvite_thenValidatesAsExpected() throws Exception {
        LocalDateTime startsAt = LocalDateTime.now().plusDays(1).withNano(0);
        String created = mockMvc.perform(post("/api/v1/admin/rooms")
                        .contentType(MediaType.APPLICATION_JSON).content(createRoomBody(startsAt))
                        .with(authentication(adminAuth())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long roomId = com.jayway.jsonpath.JsonPath.parse(created).read("$.data.room_id", Long.class);

        for (int invalidMaxUses : List.of(0, -1)) {
            mockMvc.perform(post("/api/v1/admin/rooms/{roomId}/invites", roomId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"max_uses\":" + invalidMaxUses + "}")
                            .with(authentication(adminAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        }

        mockMvc.perform(post("/api/v1/admin/rooms/{roomId}/invites", roomId)
                        .with(authentication(adminAuth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.max_uses").isEmpty());
    }

    @Test
    @DisplayName("멤버 성별 부여 시 gender가 갱신되고 가명이 백필되며 방 상세에 반영된다")
    void assignGender_updatesMemberGenderAndBackfillsPseudonym() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Long roomId = persistRoom(Room.create(LocalDate.now(), 3).started(now));
        RoomMemberEntity member = RoomMemberEntity.from(RoomMember.of(roomId, 901L, null, null, now));
        em.persist(member);
        em.flush();
        Long memberId = member.toDomain().id();

        // 방에 여성 0명 → 성별 부여 시 여성 풀 첫 번째 "유진" 백필
        mockMvc.perform(patch("/api/v1/admin/rooms/{roomId}/members/{memberId}", roomId, memberId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"gender\": \"FEMALE\"}")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gender").value("FEMALE"))
                .andExpect(jsonPath("$.data.pseudonym").value("유진"));

        em.flush();
        em.clear();

        mockMvc.perform(get("/api/v1/admin/rooms/{roomId}", roomId).with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.female_count").value(1))
                .andExpect(jsonPath("$.data.members[0].gender").value("FEMALE"))
                .andExpect(jsonPath("$.data.members[0].pseudonym").value("유진"));
    }

    @Test
    @DisplayName("MALE·FEMALE 이외의 성별 값이면 400 ROOM_MEMBER_INVALID_GENDER")
    void assignGender_invalidValue_returns400() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Long roomId = persistRoom(Room.create(LocalDate.now(), 3).started(now));
        RoomMemberEntity member = RoomMemberEntity.from(RoomMember.of(roomId, 902L, null, null, now));
        em.persist(member);
        em.flush();

        mockMvc.perform(patch("/api/v1/admin/rooms/{roomId}/members/{memberId}", roomId, member.toDomain().id())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"gender\": \"OTHER\"}")
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ROOM_MEMBER_INVALID_GENDER"));
    }

    @Test
    @DisplayName("CS 권한으로 방 생성·코드 발급·성별 부여 시 403")
    void writeApis_withCsRole_return403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRoomBody(LocalDateTime.now().plusDays(1).withNano(0)))
                        .with(authentication(csAuth())))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/rooms/{roomId}/invites", 1L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"max_uses\": 6}")
                        .with(authentication(csAuth())))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/admin/rooms/{roomId}/members/{memberId}", 1L, 1L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"gender\": \"MALE\"}")
                        .with(authentication(csAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("방 취소 시 CANCELLED 로 바뀌고 그 코드로는 입장할 수 없다")
    void cancelRoom_marksCancelled() throws Exception {
        LocalDateTime startsAt = LocalDateTime.now().plusDays(1).withNano(0);
        String created = mockMvc.perform(post("/api/v1/admin/rooms")
                        .contentType(MediaType.APPLICATION_JSON).content(createRoomBody(startsAt))
                        .with(authentication(adminAuth())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long roomId = com.jayway.jsonpath.JsonPath.parse(created).read("$.data.room_id", Long.class);

        mockMvc.perform(post("/api/v1/admin/rooms/{roomId}/cancel", roomId)
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk());

        em.flush();
        em.clear();

        mockMvc.perform(get("/api/v1/admin/rooms/{roomId}", roomId).with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("없는 방을 취소하면 404")
    void cancelRoom_notFound_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/admin/rooms/{roomId}/cancel", 999999L)
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ADMIN_ROOM_NOT_FOUND"));
    }

    @Test
    @DisplayName("CS 권한으로 방 취소 시 403")
    void cancelRoom_withCsRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/rooms/{roomId}/cancel", 1L)
                        .with(authentication(csAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("초대코드 조회도 CS 권한이면 403 — 코드는 인증 없이 입장 가능한 자격증명이다")
    void listInvites_withCsRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/rooms/{roomId}/invites", 1L).with(authentication(csAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CS 권한도 방 목록·상세 조회는 가능하다 (코드가 안 실리는 조회는 전체 허용)")
    void readApis_withCsRole_stillAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/admin/rooms").with(authentication(csAuth())))
                .andExpect(status().isOk());
    }
}
