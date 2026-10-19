package org.son.haruharu.domain.room;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.identity.implement.UserIdentity;
import org.son.haruharu.domain.identity.implement.UserIdentityWriter;
import org.son.haruharu.domain.profile.implement.Gender;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.storage.UserProfileEntity;
import org.son.haruharu.domain.profile.storage.UserProfileJpaRepository;
import org.son.haruharu.domain.room.business.RoomInviteService;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.son.haruharu.domain.room.storage.RoomInviteJpaRepository;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.son.haruharu.utils.JwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 초대코드 입장 PASS 본인확인 게이트(#514). 플래그를 켠 실제 HTTP·영속성으로 확인한다. 플래그 OFF 동작은 RoomJoinApiIntegrationTest. */
@SpringBootTest(properties = "haruharu.room.join-identity-required=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomJoinIdentityGateIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserJpaRepository userJpaRepository;
    @Autowired UserProfileJpaRepository userProfileJpaRepository;
    @Autowired UserIdentityWriter userIdentityWriter;
    @Autowired RoomWriter roomWriter;
    @Autowired RoomInviteService roomInviteService;
    @Autowired RoomMemberReader roomMemberReader;
    @Autowired RoomMemberWriter roomMemberWriter;
    @Autowired RoomInviteJpaRepository roomInviteJpaRepository;

    @BeforeEach
    void clean() {
        // 같은 H2 를 다른 테스트 컨텍스트와 공유한다 — 방·코드는 매번 새로 만들고 사람 관련 행만 비운다.
        for (String table : List.of("user_identities", "user_profiles", "users")) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }

    @Test
    @DisplayName("PASS 미인증 계정의 새 입장은 403 IDENTITY_REQUIRED 이고 멤버·코드 사용 횟수가 바뀌지 않는다")
    void givenNoPass_whenJoin_thenForbiddenIdentityRequired() throws Exception {
        Long userId = user(Gender.MALE);
        Long roomId = joinableRoom();
        String code = roomInviteService.issueFor(roomId, 6, null, "admin").code();

        join(userId, code)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.error.code").value("IDENTITY_REQUIRED"));

        assertThat(roomMemberReader.getByRoomId(roomId)).isEmpty();
        assertThat(roomInviteJpaRepository.findByCode(code).orElseThrow().toDomain().usedCount()).isZero();
    }

    @Test
    @DisplayName("PASS 인증 계정은 입장하고 방 멤버 성별은 프로필이 아닌 PASS 값이며 생년 스냅샷이 남는다")
    void givenPass_whenJoin_thenOkAndMemberUsesPassGenderAndBirthday() throws Exception {
        Long userId = user(Gender.MALE);
        LocalDate birthday = LocalDate.of(2001, 3, 4);
        pass(userId, "FEMALE", birthday);
        Long roomId = joinableRoom();
        String code = roomInviteService.issueFor(roomId, 6, null, "admin").code();

        join(userId, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.room_id").value(roomId.intValue()));

        RoomMember member = roomMemberReader.findByRoomIdAndUserId(roomId, userId).orElseThrow();
        assertThat(member.gender()).isEqualTo("FEMALE");
        // 생년 값 자체는 RoomJoinPassSnapshotPostgresIntegrationTest 에서 본다. H2 는 JVM 이 UTC 로 뜬 뒤 TimeZoneConfig 가
        // 기본 시간대를 바꾸면 LocalDate 를 쓸 때마다 하루씩 밀려 저장한다(CI). PostgreSQL 은 그렇지 않다.
        assertThat(member.birthday()).isNotNull();
        assertThat(member.pseudonym()).isNotNull();
    }

    @Test
    @DisplayName("이미 멤버인 PASS 미인증 계정의 재입장은 지금처럼 200 이다")
    void givenExistingMemberWithoutPass_whenJoinAgain_thenOk() throws Exception {
        Long userId = user(Gender.MALE);
        Long roomId = joinableRoom();
        String code = roomInviteService.issueFor(roomId, 6, null, "admin").code();
        roomMemberWriter.save(RoomMember.of(roomId, userId, "MALE", null, LocalDateTime.now().minusMinutes(5)));

        join(userId, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.room_id").value(roomId.intValue()));

        assertThat(roomMemberReader.getByRoomId(roomId)).hasSize(1);
    }

    private Long user(Gender gender) {
        Long userId = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        userProfileJpaRepository.save(UserProfileEntity.from(
                new UserProfile(null, userId, null, null, null, null, "닉" + userId, null, null, gender)));
        return userId;
    }

    private void pass(Long userId, String gender, LocalDate birthday) {
        userIdentityWriter.save(new UserIdentity(null, userId, "ci-" + userId, "di-" + userId, "01099998888",
                birthday, gender, "홍길동", "SKT", false, LocalDateTime.now()));
    }

    private Long joinableRoom() {
        LocalDateTime starts = LocalDateTime.now().plusHours(1); // now < startsAt → PENDING(입장 가능)
        Room room = roomWriter.save(Room.createScheduled(starts, starts.plusDays(2).withHour(20),
                starts.plusDays(2).withHour(22), starts.plusDays(2).withHour(23),
                starts.plusDays(2).withHour(23).withMinute(30), 3));
        return room.id();
    }

    private ResultActions join(Long userId, String code) throws Exception {
        return mockMvc.perform(post("/api/v1/rooms/join")
                .header("Authorization", "Bearer " + jwtProvider.generateAccessToken(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"));
    }
}
