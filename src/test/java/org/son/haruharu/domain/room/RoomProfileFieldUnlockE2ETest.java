package org.son.haruharu.domain.room;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.profile.implement.ProfileFieldCatalog;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.storage.ProfileFieldCatalogEntity;
import org.son.haruharu.domain.profile.storage.ProfileFieldCatalogJpaRepository;
import org.son.haruharu.domain.profile.storage.ProfileFieldUnlockJpaRepository;
import org.son.haruharu.domain.profile.storage.UserProfileEntity;
import org.son.haruharu.domain.profile.storage.UserProfileJpaRepository;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.storage.RoomEntity;
import org.son.haruharu.domain.room.storage.RoomJpaRepository;
import org.son.haruharu.domain.room.storage.RoomMemberEntity;
import org.son.haruharu.domain.room.storage.RoomMemberJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 프로필 항목 열쇠 해금 E2E. 실제 HTTP(컨트롤러→서비스→JPA/H2) 전 계층을 관통한다.
 * 카탈로그 기본값(affiliation 10/3, height 15/3, mbti 5/3)을 전제로 검증.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomProfileFieldUnlockE2ETest {

    @Autowired MockMvc mockMvc;
    @Autowired RoomJpaRepository roomJpaRepository;
    @Autowired RoomMemberJpaRepository roomMemberJpaRepository;
    @Autowired UserProfileJpaRepository userProfileJpaRepository;
    @Autowired ProfileFieldUnlockJpaRepository profileFieldUnlockJpaRepository;
    @Autowired ProfileFieldCatalogJpaRepository profileFieldCatalogJpaRepository;

    @BeforeEach
    void setUp() {
        profileFieldUnlockJpaRepository.deleteAll();
        roomMemberJpaRepository.deleteAll();
        roomJpaRepository.deleteAll();
        userProfileJpaRepository.deleteAll();
        ensureCatalog("affiliation", 10, 3);
        ensureCatalog("height", 15, 3);
        ensureCatalog("mbti", 5, 3);
    }

    private void ensureCatalog(String key, int cost, int day) {
        if (!profileFieldCatalogJpaRepository.existsByFieldKey(key)) {
            profileFieldCatalogJpaRepository.save(ProfileFieldCatalogEntity.from(ProfileFieldCatalog.of(key, cost, day)));
        }
    }

    private static UsernamePasswordAuthenticationToken auth(UUID userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private UUID saveRoom(int currentDay) {
        Room room = new Room(null, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 24), 3, currentDay,
                LocalDateTime.now(), null);
        return roomJpaRepository.save(RoomEntity.from(room)).toDomain().id();
    }

    private UUID saveMember(UUID roomId, UUID userId, int pointSum) {
        RoomMember member = new RoomMember(null, roomId, userId, "FEMALE", pointSum, LocalDateTime.now());
        return roomMemberJpaRepository.save(RoomMemberEntity.from(member)).toDomain().id();
    }

    private void saveProfile(UUID userId) {
        UserProfile profile = new UserProfile(null, userId, "FEMALE", LocalDate.of(2000, 1, 1),
                "서울", "카카오", 165, "INFP", "하루", "SOMETIMES", "NONE", "NONE");
        userProfileJpaRepository.save(UserProfileEntity.from(profile));
    }

    @Test
    @DisplayName("E2E: Day3에서 잠금→해금(차감 30→20)→재조회 값 노출→멱등(재차감 없음)")
    void day3UnlockFlow() throws Exception {
        UUID meUserId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        UUID roomId = saveRoom(3);
        saveProfile(targetUserId);
        saveMember(roomId, meUserId, 30);
        UUID targetId = saveMember(roomId, targetUserId, 0);

        // 1) 해금 전: affiliation은 unlockable(Day3)이지만 미해금·값 가림
        mockMvc.perform(get("/api/v1/rooms/{r}/members/{m}/profile", roomId, targetId)
                        .with(authentication(auth(meUserId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.unlockable_fields[?(@.field_key=='affiliation' && @.unlockable==true && @.unlocked==false && @.value==null)]")
                        .exists());

        // 2) 해금: 열쇠 10 차감 → 20, 값 반환
        mockMvc.perform(post("/api/v1/rooms/{r}/members/{m}/profile-fields/{f}/unlock", roomId, targetId, "affiliation")
                        .with(authentication(auth(meUserId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.field_key").value("affiliation"))
                .andExpect(jsonPath("$.data.value").value("카카오"))
                .andExpect(jsonPath("$.data.remaining_point_sum").value(20));

        // 3) 재조회: affiliation 해금·값 노출, mbti는 여전히 미해금
        mockMvc.perform(get("/api/v1/rooms/{r}/members/{m}/profile", roomId, targetId)
                        .with(authentication(auth(meUserId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.unlockable_fields[?(@.field_key=='affiliation' && @.unlocked==true && @.value=='카카오')]")
                        .exists())
                .andExpect(jsonPath(
                        "$.data.unlockable_fields[?(@.field_key=='mbti' && @.unlocked==false && @.value==null)]")
                        .exists());

        // 4) 멱등: 다시 해금해도 재차감 없음 → 여전히 20
        mockMvc.perform(post("/api/v1/rooms/{r}/members/{m}/profile-fields/{f}/unlock", roomId, targetId, "affiliation")
                        .with(authentication(auth(meUserId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remaining_point_sum").value(20));
    }

    @Test
    @DisplayName("E2E: Day3 이전 해금 시도는 409(ROOM_409_FIELD_LOCKED_DAY)")
    void beforeDay3Returns409() throws Exception {
        UUID meUserId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        UUID roomId = saveRoom(2);
        saveProfile(targetUserId);
        saveMember(roomId, meUserId, 30);
        UUID targetId = saveMember(roomId, targetUserId, 0);

        mockMvc.perform(post("/api/v1/rooms/{r}/members/{m}/profile-fields/{f}/unlock", roomId, targetId, "affiliation")
                        .with(authentication(auth(meUserId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ROOM_409_FIELD_LOCKED_DAY"));

        // 조회 시에도 unlockable=false
        mockMvc.perform(get("/api/v1/rooms/{r}/members/{m}/profile", roomId, targetId)
                        .with(authentication(auth(meUserId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.unlockable_fields[?(@.field_key=='affiliation' && @.unlockable==false)]")
                        .exists());
    }

    @Test
    @DisplayName("E2E: 열쇠 부족 시 400(ROOM_INSUFFICIENT_KEYS), 차감 없음")
    void insufficientKeysReturns400() throws Exception {
        UUID meUserId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        UUID roomId = saveRoom(3);
        saveProfile(targetUserId);
        saveMember(roomId, meUserId, 5); // affiliation cost 10 미만
        UUID targetId = saveMember(roomId, targetUserId, 0);

        mockMvc.perform(post("/api/v1/rooms/{r}/members/{m}/profile-fields/{f}/unlock", roomId, targetId, "affiliation")
                        .with(authentication(auth(meUserId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ROOM_INSUFFICIENT_KEYS"));
    }

    @Test
    @DisplayName("E2E: 존재하지 않는 항목 key는 400(PROFILE_INVALID_FIELD)")
    void invalidFieldKeyReturns400() throws Exception {
        UUID meUserId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        UUID roomId = saveRoom(3);
        saveProfile(targetUserId);
        saveMember(roomId, meUserId, 30);
        UUID targetId = saveMember(roomId, targetUserId, 0);

        mockMvc.perform(post("/api/v1/rooms/{r}/members/{m}/profile-fields/{f}/unlock", roomId, targetId, "unknown")
                        .with(authentication(auth(meUserId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PROFILE_INVALID_FIELD"));
    }

    @Test
    @DisplayName("E2E: 멤버 목록은 기본정보만 담고 해금 대상 항목(affiliation 등)은 포함하지 않는다")
    void membersListHasBasicsOnly() throws Exception {
        UUID meUserId = UUID.randomUUID();
        UUID roomId = saveRoom(3);
        saveProfile(meUserId);
        saveMember(roomId, meUserId, 15);

        mockMvc.perform(get("/api/v1/rooms/{r}/members", roomId)
                        .with(authentication(auth(meUserId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.my_point_sum").value(15))
                .andExpect(jsonPath("$.data.members[0].nickname").value("하루"))
                .andExpect(jsonPath("$.data.members[0].location").value("서울"))
                .andExpect(jsonPath("$.data.members[0].affiliation").doesNotExist())
                .andExpect(jsonPath("$.data.members[0].mbti").doesNotExist());
    }
}