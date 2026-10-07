package org.son.haruharu.admin.domain.mission.common.application;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.son.haruharu.admin.domain.account.implement.AdminRole;
import org.son.haruharu.admin.security.AdminJwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:admin486http;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminRoomMissionHttpTest {
    @Autowired AdminJwtProvider tokens;
    @Autowired JsonMapper json;
    @LocalServerPort int port;

    HttpResponse<String> request(String method, String path, String body, AdminRole role) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:"+port+path))
                .header("Authorization","Bearer "+tokens.generateAccessToken(1L,role))
                .header("Content-Type","application/json")
                .method(method,body==null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build();
        try(var client=HttpClient.newHttpClient()) { return client.send(request,HttpResponse.BodyHandlers.ofString()); }
    }

    @Test
    void givenOperatorAndCsTokens_whenUsingRealHttp_thenPersistMissionAndEnforceRoles() throws Exception {
        var start=LocalDateTime.now().plusDays(1).withNano(0);
        var created=request("POST","/api/v1/admin/rooms","""
            {"starts_at":"%s","final_selection_opens_at":"%s","final_selection_closes_at":"%s",
             "result_at":"%s","ends_at":"%s","total_days":3,"missions":[
                {"type":"ANNOUNCEMENT","details":{"content":"실제 HTTP 미션"},"opens_at":"%s"}]}
            """.formatted(start,start.plusHours(60),start.plusHours(61),start.plusHours(62),start.plusHours(72),start),AdminRole.OPERATOR);
        assertThat(created.statusCode()).isEqualTo(201);
        long room=json.readTree(created.body()).path("data").path("room_id").asLong();
        String base="/api/v1/admin/rooms/"+room+"/missions";
        var listed=request("GET",base,null,AdminRole.CS);
        assertThat(listed.statusCode()).isEqualTo(200);
        var mission=json.readTree(listed.body()).path("data").path("missions").get(0);
        assertThat(mission.path("details").path("content").asText()).isEqualTo("실제 HTTP 미션");
        assertThat(mission.path("status").asText()).isEqualTo("SCHEDULED");
        assertThat(mission.path("closes_at").isNull()).isTrue();
        String path=base+"/"+mission.path("mission_id").asLong();
        assertThat(request("POST",path+"/cancel",null,AdminRole.CS).statusCode()).isEqualTo(403);
        assertThat(request("GET",path+"/announcements",null,AdminRole.CS).body()).contains("\"announcements\":[]");
        assertThat(request("POST",path+"/cancel",null,AdminRole.OPERATOR).body()).contains("\"status\":\"CANCELED\"");
        assertThat(request("GET","/api/v1/admin/rooms/"+room+"/schedule",null,AdminRole.OPERATOR).statusCode()).isEqualTo(404);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void givenScheduledAnnouncement_whenDefinitionChangeConflicts_thenExplainConflictAndPreserveBoth(boolean removeRevealFields) throws Exception {
        var start = LocalDateTime.now().plusDays(1).withSecond(0).withNano(0);
        var createdRoom = request("POST", "/api/v1/admin/rooms", """
                {"starts_at":"%s","final_selection_opens_at":"%s","final_selection_closes_at":"%s",
                 "result_at":"%s","ends_at":"%s","total_days":3,"missions":[]}
                """.formatted(start, start.plusHours(60), start.plusHours(61), start.plusHours(62), start.plusHours(72)),
                AdminRole.OPERATOR);
        assertThat(createdRoom.statusCode()).isEqualTo(201);
        long roomId = json.readTree(createdRoom.body()).path("data").path("room_id").asLong();
        String base = "/api/v1/admin/rooms/" + roomId + "/missions";
        var createdMission = request("POST", base, """
                {"type":"ANNOUNCEMENT","details":{"content":"원래 정의","reveal_fields":["MBTI"]},
                 "opens_at":"%s","announcement":{"announcement_type":"START","run_at":"%s",
                 "content":%s,"push":false}}
                """.formatted(start, start, removeRevealFields ? "null" : "\"시작 안내\""), AdminRole.OPERATOR);
        assertThat(createdMission.statusCode()).isEqualTo(201);
        long missionId = json.readTree(createdMission.body()).path("data").path("mission_id").asLong();
        String path = base + "/" + missionId;
        var missionBefore = json.readTree(request("GET", path, null, AdminRole.CS).body()).path("data");
        var plansBefore = json.readTree(request("GET", path + "/announcements", null, AdminRole.CS).body()).path("data");
        long announcementId = plansBefore.path("announcements").get(0).path("announcement_id").asLong();
        var nextStart = removeRevealFields ? start : start.plusHours(1);
        String update = """
                {"type":"ANNOUNCEMENT","details":{"content":"바꾼 정의","reveal_fields":%s},"opens_at":"%s"}
                """.formatted(removeRevealFields ? "[]" : "[\"MBTI\"]", nextStart);

        var rejected = request("PUT", path, update, AdminRole.OPERATOR);
        assertThat(rejected.statusCode()).isEqualTo(409);
        var error = json.readTree(rejected.body()).path("error");
        assertThat(error.path("code").asText()).isEqualTo("MISSION_ANNOUNCEMENT_CONFLICT");
        assertThat(error.path("message").asText()).contains("예약된 안내", "수정", "취소");
        assertThat(error.has("fields")).isFalse();
        assertThat(json.readTree(request("GET", path, null, AdminRole.CS).body()).path("data")).isEqualTo(missionBefore);
        assertThat(json.readTree(request("GET", path + "/announcements", null, AdminRole.CS).body()).path("data")).isEqualTo(plansBefore);

        var invalidInput = request("PUT", path, """
                {"type":"ANNOUNCEMENT","details":{"content":null,"reveal_fields":[]},"opens_at":"%s"}
                """.formatted(start), AdminRole.OPERATOR);
        assertThat(invalidInput.statusCode()).isEqualTo(400);
        assertThat(json.readTree(invalidInput.body()).path("error").path("fields").get(0).path("field").asText())
                .isEqualTo("details.content");

        // 시간 충돌은 안내를 뒤로 옮기고, 공개 항목 충돌은 안내 본문을 채운 뒤 같은 미션 변경을 재시도한다.
        var corrected = request("PUT", path + "/announcements/" + announcementId, """
                {"announcement_type":"START","run_at":"%s","content":"수정한 안내","push":false}
                """.formatted(nextStart), AdminRole.OPERATOR);
        assertThat(corrected.statusCode()).isEqualTo(200);
        var accepted = request("PUT", path, update, AdminRole.OPERATOR);
        assertThat(accepted.statusCode()).isEqualTo(200);
        assertThat(json.readTree(accepted.body()).path("data").path("details").path("content").asText()).isEqualTo("바꾼 정의");
    }
}
