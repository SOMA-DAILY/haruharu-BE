package org.son.haruharu.domain.room;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.profile.implement.UserImage;
import org.son.haruharu.domain.profile.storage.UserImageEntity;
import org.son.haruharu.domain.profile.storage.UserImageJpaRepository;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.storage.RoomEntity;
import org.son.haruharu.domain.room.storage.RoomJpaRepository;
import org.son.haruharu.domain.room.storage.RoomMemberEntity;
import org.son.haruharu.domain.room.storage.RoomMemberJpaRepository;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import jakarta.persistence.EntityManagerFactory;

import static org.hamcrest.Matchers.nullValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
class RoomListApiIntegrationTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 26, 12, 0);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-06-26T03:00:00Z"), SEOUL);
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired RoomJpaRepository roomJpaRepository;
    @Autowired RoomMemberJpaRepository roomMemberJpaRepository;
    @Autowired UserImageJpaRepository userImageJpaRepository;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired JsonMapper jsonMapper;
    @MockitoBean S3StoragePort s3StoragePort;

    @BeforeEach
    void clean() {
        userImageJpaRepository.deleteAll();
        roomMemberJpaRepository.deleteAll();
        roomJpaRepository.deleteAll();
        given(s3StoragePort.createPresignedImageGetUrl(anyString()))
                .willAnswer(invocation -> invocation.getArgument(0) + "?signed");
    }

    @Test
    @DisplayName("방 목록은 snake_case와 null 이미지 위치를 보존한 현재 카드만 반환한다")
    void givenCurrentAndHistoricalMemberships_whenGetRooms_thenReturnsSnakeCaseCurrentCards() throws Exception {
        Long userId = 500L;
        Long activeRoomId = saveRoom(NOW.minusHours(1), NOW.plusHours(2), NOW.plusHours(3), NOW.plusHours(4), NOW.plusHours(6));
        Long selectingRoomId = saveRoom(NOW.minusDays(2), NOW.minusHours(1), NOW.plusHours(1), NOW.plusHours(2), NOW.plusDays(1));
        Long pendingRoomId = saveRoom(NOW.plusHours(1), NOW.plusHours(2), NOW.plusHours(3), NOW.plusHours(4), NOW.plusHours(5));
        Long endedRoomId = saveRoom(NOW.minusHours(6), NOW.minusHours(5), NOW.minusHours(4), NOW.minusHours(3), NOW);
        Long cancelledRoomId = saveRoom(RoomStatus.CANCELLED, NOW.minusHours(1), NOW.plusHours(2), NOW.plusHours(3), NOW.plusHours(4), NOW.plusHours(6));
        saveMember(activeRoomId, userId);
        saveMember(activeRoomId, 501L);
        saveMember(activeRoomId, 502L);
        saveMember(activeRoomId, 504L);
        saveMember(activeRoomId, 505L);
        saveMember(activeRoomId, 506L);
        saveMember(activeRoomId, 507L);
        saveMember(endedRoomId, userId);
        saveMember(selectingRoomId, userId);
        saveMember(pendingRoomId, userId);
        saveMember(cancelledRoomId, userId);
        Long otherUserRoomId = saveRoom(NOW.minusHours(1), NOW.plusHours(2), NOW.plusHours(3), NOW.plusHours(4), NOW.plusHours(6));
        saveMember(otherUserRoomId, 503L);
        userImageJpaRepository.save(UserImageEntity.from(new UserImage(null, userId, "https://image/self", 0)));
        userImageJpaRepository.save(UserImageEntity.from(new UserImage(null, 502L, "https://image/third", 0)));
        userImageJpaRepository.save(UserImageEntity.from(new UserImage(null, 507L, "https://image/seventh", 0)));

        MvcResult result = mockMvc.perform(get("/api/v1/rooms").with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.now").value("2026-06-26T12:00:00"))
                .andExpect(jsonPath("$.data.rooms.length()").value(3))
                .andExpect(jsonPath("$.data.rooms[0].room_id").value(activeRoomId.intValue()))
                .andExpect(jsonPath("$.data.rooms[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.rooms[0].current_day").value(1))
                .andExpect(jsonPath("$.data.rooms[0].total_days").value(3))
                .andExpect(jsonPath("$.data.rooms[0].profile_image_urls.length()").value(7))
                .andExpect(jsonPath("$.data.rooms[0].profile_image_urls[0]").value("https://image/self?signed"))
                .andExpect(jsonPath("$.data.rooms[0].profile_image_urls[1]").value(nullValue()))
                .andExpect(jsonPath("$.data.rooms[0].profile_image_urls[2]").value("https://image/third?signed"))
                .andExpect(jsonPath("$.data.rooms[0].profile_image_urls[3]").value(nullValue()))
                .andExpect(jsonPath("$.data.rooms[0].profile_image_urls[4]").value(nullValue()))
                .andExpect(jsonPath("$.data.rooms[0].profile_image_urls[5]").value(nullValue()))
                .andExpect(jsonPath("$.data.rooms[0].profile_image_urls[6]").value("https://image/seventh?signed"))
                .andExpect(jsonPath("$.data.rooms[0].timer_type").value("FINAL_SELECTION_OPENS_AT"))
                .andExpect(jsonPath("$.data.rooms[0].timer_target_at").value("2026-06-26T14:00:00"))
                .andExpect(jsonPath("$.data.rooms[0].refresh_at").value("2026-06-26T14:00:00"))
                .andExpect(jsonPath("$.data.rooms[0].member_count").doesNotExist())
                .andExpect(jsonPath("$.data.rooms[0].members").doesNotExist())
                .andExpect(jsonPath("$.data.rooms[1].room_id").value(selectingRoomId.intValue()))
                .andExpect(jsonPath("$.data.rooms[1].status").value("SELECTING"))
                .andExpect(jsonPath("$.data.rooms[2].room_id").value(pendingRoomId.intValue()))
                .andExpect(jsonPath("$.data.rooms[2].status").value("PENDING"))
                .andReturn();

        JsonNode room = jsonMapper.readTree(result.getResponse().getContentAsString()).path("data").path("rooms").get(0);
        List<String> fields = new ArrayList<>();
        fields.addAll(room.propertyNames());
        assertThat(fields).containsExactlyInAnyOrder(
                "room_id", "status", "current_day", "total_days", "profile_image_urls",
                "timer_type", "timer_target_at", "refresh_at");
    }

    @Test
    @DisplayName("종료 방 포함 옵션은 내 종료 방만 반환하고 취소 및 다른 회원 방은 제외한다")
    void givenHistoricalMemberships_whenIncludeEnded_thenReturnsOnlyMyNonCancelledRooms() throws Exception {
        Long userId = 600L;
        Long endedRoomId = saveRoom(NOW.minusHours(6), NOW.minusHours(5), NOW.minusHours(4), NOW.minusHours(3), NOW);
        Long cancelledRoomId = saveRoom(RoomStatus.CANCELLED, NOW.minusHours(6), NOW.minusHours(5), NOW.minusHours(4), NOW.minusHours(3), NOW);
        Long otherRoomId = saveRoom(NOW.minusHours(6), NOW.minusHours(5), NOW.minusHours(4), NOW.minusHours(3), NOW);
        saveMember(endedRoomId, userId);
        saveMember(cancelledRoomId, userId);
        saveMember(otherRoomId, 601L);

        mockMvc.perform(get("/api/v1/rooms").param("include_ended", "true").with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rooms.length()").value(1))
                .andExpect(jsonPath("$.data.rooms[0].room_id").value(endedRoomId.intValue()))
                .andExpect(jsonPath("$.data.rooms[0].status").value("ENDED"))
                .andExpect(jsonPath("$.data.rooms[0].timer_type").value(nullValue()))
                .andExpect(jsonPath("$.data.rooms[0].refresh_at").value(nullValue()));
    }

    @Test
    @DisplayName("종료 방 이력은 20개씩 중복 없이 다음 커서로 조회한다")
    void givenMoreThanTwentyEndedRooms_whenPaging_thenReturnsRemainingHistory() throws Exception {
        Long userId = 610L;
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            Long id = saveRoom(NOW.minusHours(6), NOW.minusHours(5), NOW.minusHours(4), NOW.minusHours(3), NOW);
            ids.add(id);
            saveMember(id, userId);
        }
        mockMvc.perform(get("/api/v1/rooms").param("include_ended", "true").with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rooms.length()").value(20))
                .andExpect(jsonPath("$.data.rooms[0].room_id").value(ids.get(20).intValue()))
                .andExpect(jsonPath("$.data.next_ended_cursor").value(ids.get(1).intValue()));
        mockMvc.perform(get("/api/v1/rooms").param("include_ended", "true")
                        .param("ended_before", ids.get(1).toString()).with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rooms.length()").value(1))
                .andExpect(jsonPath("$.data.rooms[0].room_id").value(ids.get(0).intValue()))
                .andExpect(jsonPath("$.data.next_ended_cursor").value(nullValue()));
    }

    @Test
    @DisplayName("방 이력이 없으면 404 대신 빈 rooms 배열을 200으로 반환한다")
    void givenNoMembership_whenGetRooms_thenReturnsEmptyRoomsWith200() throws Exception {
        mockMvc.perform(get("/api/v1/rooms").with(authentication(auth(999L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rooms").isArray())
                .andExpect(jsonPath("$.data.rooms").isEmpty());
    }

    @Test
    @DisplayName("result_at 정확히는 null 타이머와 ends_at refresh를 반환하고 ends_at부터 방을 제외한다")
    void givenResultAndEndBoundaries_whenGetRooms_thenUsesExactBoundaryContract() throws Exception {
        Long userId = 700L;
        Long resultRoomId = saveRoom(NOW.minusHours(5), NOW.minusHours(4), NOW.minusHours(3), NOW, NOW.plusHours(1));
        Long endedRoomId = saveRoom(NOW.minusHours(5), NOW.minusHours(4), NOW.minusHours(3), NOW.minusHours(1), NOW);
        saveMember(resultRoomId, userId);
        saveMember(endedRoomId, userId);

        mockMvc.perform(get("/api/v1/rooms").with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rooms.length()").value(1))
                .andExpect(jsonPath("$.data.rooms[0].room_id").value(resultRoomId.intValue()))
                .andExpect(jsonPath("$.data.rooms[0].timer_type").value(nullValue()))
                .andExpect(jsonPath("$.data.rooms[0].timer_target_at").value(nullValue()))
                .andExpect(jsonPath("$.data.rooms[0].refresh_at").value("2026-06-26T13:00:00"));
    }

    @Test
    @DisplayName("현재 방이 1개에서 20개로 늘어도 목록 조회 SQL 수는 증가하지 않는다")
    void givenOneAndManyCurrentRooms_whenGetRooms_thenPreparedStatementCountHasZeroGrowth() throws Exception {
        Long userId = 800L;
        seedCurrentRooms(userId, 1);
        long oneRoomStatements = countRoomListStatements(userId);

        clean();
        seedCurrentRooms(userId, 20);
        long manyRoomStatements = countRoomListStatements(userId);

        assertThat(oneRoomStatements).isEqualTo(4L);
        assertThat(manyRoomStatements).isEqualTo(4L);
    }

    private long countRoomListStatements(Long userId) throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        assertThat(statistics.isStatisticsEnabled()).isTrue();
        statistics.clear();

        mockMvc.perform(get("/api/v1/rooms").with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rooms").isArray());

        return statistics.getPrepareStatementCount();
    }

    private void seedCurrentRooms(Long userId, int count) {
        for (int index = 0; index < count; index++) {
            Long roomId = saveRoom(NOW.minusHours(1), NOW.plusHours(2), NOW.plusHours(3), NOW.plusHours(4), NOW.plusHours(6));
            saveMember(roomId, userId);
            saveMember(roomId, 9_000L + index);
            userImageJpaRepository.save(UserImageEntity.from(new UserImage(null, 9_000L + index,
                    "https://image/" + index, 0)));
        }
    }

    private Long saveRoom(LocalDateTime startsAt, LocalDateTime opensAt, LocalDateTime closesAt,
                          LocalDateTime resultAt, LocalDateTime endsAt) {
        return saveRoom(RoomStatus.PENDING, startsAt, opensAt, closesAt, resultAt, endsAt);
    }

    private Long saveRoom(RoomStatus status, LocalDateTime startsAt, LocalDateTime opensAt, LocalDateTime closesAt,
                          LocalDateTime resultAt, LocalDateTime endsAt) {
        Room room = new Room(null, status, startsAt.toLocalDate(), 3, startsAt, null,
                startsAt, opensAt, closesAt, resultAt, endsAt);
        return roomJpaRepository.save(RoomEntity.from(room)).toDomain().id();
    }

    private void saveMember(Long roomId, Long userId) {
        roomMemberJpaRepository.save(RoomMemberEntity.from(
                RoomMember.of(roomId, userId, "MALE", LocalDate.of(2000, 1, 1), NOW)));
    }

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }
}
