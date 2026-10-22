package org.son.haruharu.admin.domain.room;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.cohort.implement.Cohort;
import org.son.haruharu.domain.cohort.implement.CohortStatus;
import org.son.haruharu.domain.cohort.implement.CohortWriter;
import org.son.haruharu.domain.cohort.implement.RecruitAudience;
import org.son.haruharu.domain.cohort.implement.RecruitTrack;
import org.son.haruharu.domain.cohort.implement.RecruitTrackWriter;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserImage;
import org.son.haruharu.domain.profile.storage.UserImageEntity;
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
    @DisplayName("관리자 방 상세에 해당 참여자의 정렬된 대표사진 원본이 포함되고 사진 없는 참여자는 null이다")
    void givenMemberPhotos_whenAdminReadsRoom_thenReturnsRepresentativeOriginalOnly() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Long roomId = persistRoom(Room.create(LocalDate.now(), 3).started(now));
        em.persist(RoomMemberEntity.from(RoomMember.of(roomId, 901L, "FEMALE", null, now)));
        em.persist(RoomMemberEntity.from(RoomMember.of(roomId, 902L, "MALE", null, now)));
        em.persist(UserImageEntity.from(new UserImage(null, 901L, "https://example.invalid/second.jpg", 1)));
        em.persist(UserImageEntity.from(new UserImage(null, 901L, "https://example.invalid/original.jpg", 0)));
        em.persist(UserImageEntity.from(new UserImage(null, 903L, "https://example.invalid/outsider.jpg", 0)));
        em.flush();

        mockMvc.perform(get("/api/v1/admin/rooms/{roomId}", roomId).with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members.length()").value(2))
                .andExpect(jsonPath("$.data.members[0].profile_image_url").value("https://example.invalid/original.jpg"))
                .andExpect(jsonPath("$.data.members[1].profile_image_url").hasJsonPath())
                .andExpect(jsonPath("$.data.members[1].profile_image_url").isEmpty());
    }

    @Test
    @DisplayName("참여자가 없는 방의 관리자 사진 조회도 빈 참여자 목록을 반환한다")
    void givenEmptyRoom_whenAdminReadsRoom_thenReturnsEmptyMembers() throws Exception {
        Long roomId = persistRoom(Room.create(LocalDate.now(), 3));
        mockMvc.perform(get("/api/v1/admin/rooms/{roomId}", roomId).with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members").isEmpty());
    }

    @Test
    @DisplayName("미인증 요청은 사진이 포함된 방 상세를 조회할 수 없다")
    void givenNoAuthentication_whenReadsRoomWithPhotos_thenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/rooms/1"))
                .andExpect(status().isUnauthorized());
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
                  "max_uses": 6
                }
                """.formatted(startsAt, startsAt.plusHours(60), startsAt.plusHours(63),
                startsAt.plusHours(64), startsAt.plusHours(72));
    }

    @Test
    @DisplayName("폐기된 미션 카탈로그 API는 인증된 SUPER에게도 404를 반환한다")
    void givenSuperAdmin_whenAccessRetiredMissions_thenNotFound() throws Exception {
        var superAuth = new UsernamePasswordAuthenticationToken(1L, null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER")));
        mockMvc.perform(get("/api/v1/admin/missions").with(authentication(superAuth)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/admin/missions").with(authentication(superAuth))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("자동 미션 입력 없이 방과 초대코드를 생성할 수 있다")
    void givenNoMissionFields_whenCreateRoom_thenCreatesRoomAndInvite() throws Exception {
        String body = """
                {
                  "starts_at": "2030-01-01T19:00:00",
                  "final_selection_opens_at": "2030-01-03T18:00:00",
                  "final_selection_closes_at": "2030-01-03T21:00:00",
                  "result_at": "2030-01-03T22:00:00",
                  "ends_at": "2030-01-04T19:00:00",
                  "total_days": 3,
                  "max_uses": 6
                }
                """;
        mockMvc.perform(post("/api/v1/admin/rooms")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(authentication(adminAuth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.room_id").isNumber())
                .andExpect(jsonPath("$.data.code").isString());
    }

    // ── #513 기수·이름·성별 정원·입장 경로 ──────────────────────────────────

    @Autowired RecruitTrackWriter recruitTrackWriter;
    @Autowired CohortWriter cohortWriter;

    private Long cohort(CohortStatus status) {
        RecruitTrack track = recruitTrackWriter.save(RecruitTrack.create("seoul-" + System.nanoTime(), "서울 대학생", "서울",
                RecruitAudience.STUDENT, 20, 29, List.of(), 1));
        LocalDateTime now = LocalDateTime.now();
        return cohortWriter.save(new Cohort(null, track.id(), 6, status, now.minusDays(1), now.plusDays(3),
                now.plusDays(4), "10월 마지막 주", 6, 6, null, "admin:1", null, null)).id();
    }

    private static String composedRoomBody(LocalDateTime startsAt, String composition) {
        String base = createRoomBody(startsAt).strip();
        return base.substring(0, base.length() - 1) + ", " + composition + "}";
    }

    @Test
    @DisplayName("기수·이름·성별 정원을 주고 방을 만들면 저장되고 방 상세에 함께 나온다(#513)")
    void createRoom_withComposition_savedAndShownInDetail() throws Exception {
        Long cohortId = cohort(CohortStatus.OPEN);
        String response = mockMvc.perform(post("/api/v1/admin/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(composedRoomBody(LocalDateTime.now().plusDays(1).withNano(0),
                                "\"cohort_id\": " + cohortId + ", \"name\": \" 서울 6기 1조 \", "
                                        + "\"male_capacity\": 3, \"female_capacity\": 2"))
                        .with(authentication(adminAuth())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long roomId = com.jayway.jsonpath.JsonPath.parse(response).read("$.data.room_id", Long.class);

        mockMvc.perform(get("/api/v1/admin/rooms/{roomId}", roomId).with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cohort_id").value(cohortId))
                .andExpect(jsonPath("$.data.name").value("서울 6기 1조"))
                .andExpect(jsonPath("$.data.male_capacity").value(3))
                .andExpect(jsonPath("$.data.female_capacity").value(2))
                .andExpect(jsonPath("$.data.invite_member_count").value(0));
    }

    @Test
    @DisplayName("기수·정원 없이 만든 방(웹 전용 방)은 상세에서 그 칸이 비어 있다")
    void createRoom_withoutComposition_detailFieldsAreNull() throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRoomBody(LocalDateTime.now().plusDays(1).withNano(0)))
                        .with(authentication(adminAuth())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long roomId = com.jayway.jsonpath.JsonPath.parse(response).read("$.data.room_id", Long.class);

        mockMvc.perform(get("/api/v1/admin/rooms/{roomId}", roomId).with(authentication(adminAuth())))
                .andExpect(jsonPath("$.data.cohort_id").doesNotExist())
                .andExpect(jsonPath("$.data.male_capacity").doesNotExist());
    }

    @Test
    @DisplayName("취소된 기수·없는 기수·1 미만 정원·50자 넘는 이름이면 방을 만들지 않는다")
    void createRoom_invalidComposition_rejected() throws Exception {
        LocalDateTime startsAt = LocalDateTime.now().plusDays(1).withNano(0);
        Long cancelled = cohort(CohortStatus.CANCELLED);

        mockMvc.perform(post("/api/v1/admin/rooms").contentType(MediaType.APPLICATION_JSON)
                        .content(composedRoomBody(startsAt, "\"cohort_id\": " + cancelled))
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields[0].field").value("cohort_id"));
        mockMvc.perform(post("/api/v1/admin/rooms").contentType(MediaType.APPLICATION_JSON)
                        .content(composedRoomBody(startsAt, "\"cohort_id\": 999999"))
                        .with(authentication(adminAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COHORT_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/admin/rooms").contentType(MediaType.APPLICATION_JSON)
                        .content(composedRoomBody(startsAt, "\"male_capacity\": 0"))
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/admin/rooms").contentType(MediaType.APPLICATION_JSON)
                        .content(composedRoomBody(startsAt, "\"name\": \"" + "가".repeat(51) + "\""))
                        .with(authentication(adminAuth())))
                .andExpect(status().isBadRequest());

        assertThat(em.createQuery("select count(r) from RoomEntity r", Long.class).getSingleResult()).isZero();
    }

    @Test
    @DisplayName("방 상세 참여자 표에 입장 경로·배정 신청 id·본인 확인 여부(탈퇴로 지운 인증은 미확인)가 나오고, 초대 인원은 INVITE 만 센다(#513)")
    void roomDetail_showsEntryPathAndIdentityVerified() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Long roomId = persistRoom(Room.create(LocalDate.now(), 3).started(now));
        em.persist(RoomMemberEntity.from(RoomMember.of(roomId, 901L, "FEMALE", null, now)));
        em.persist(RoomMemberEntity.from(RoomMember.fromApplication(roomId, 902L, "MALE", null, 55L, now)));
        em.createNativeQuery("insert into user_identities (id, user_id, created_at, updated_at) values (1, 902, now(), now())")
                .executeUpdate();
        // 탈퇴로 지운(soft-delete) 본인인증 행은 확인 완료로 보지 않는다.
        em.persist(RoomMemberEntity.from(RoomMember.of(roomId, 903L, "FEMALE", null, now)));
        em.createNativeQuery("insert into user_identities (id, user_id, created_at, updated_at, deleted_at)"
                + " values (2, 903, now(), now(), now())").executeUpdate();
        em.flush();

        mockMvc.perform(get("/api/v1/admin/rooms/{roomId}", roomId).with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invite_member_count").value(2))
                .andExpect(jsonPath("$.data.members[0].user_id").value(901))
                .andExpect(jsonPath("$.data.members[0].entry_path").value("INVITE"))
                .andExpect(jsonPath("$.data.members[0].application_id").doesNotExist())
                .andExpect(jsonPath("$.data.members[0].identity_verified").value(false))
                .andExpect(jsonPath("$.data.members[1].entry_path").value("APPLICATION"))
                .andExpect(jsonPath("$.data.members[1].application_id").value(55))
                .andExpect(jsonPath("$.data.members[1].identity_verified").value(true))
                .andExpect(jsonPath("$.data.members[2].user_id").value(903))
                .andExpect(jsonPath("$.data.members[2].identity_verified").value(false));
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
        assertThat(em.find(RoomEntity.class, roomId).toDomain().status().name()).isEqualTo("PENDING");
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
                  "max_uses": 6
                }
                """.formatted(startsAt, startsAt.minusHours(1), startsAt.plusDays(2),
                startsAt.plusDays(2).plusHours(1), startsAt.plusDays(3));

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
