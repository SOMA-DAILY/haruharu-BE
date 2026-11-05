package org.son.haruharu.admin.domain.audit.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAuditActionResolverTest {

    private static final String P = "/api/v1/admin";

    @Test
    @DisplayName("유저 상세 GET 은 VIEW_USER 와 USER 대상 id 로 기록된다")
    void givenUserDetailGet_whenResolve_thenViewUserWithTarget() {
        var resolved = AdminAuditActionResolver.resolve("GET", P + "/users/{userId}", Map.of("userId", "42"), 200);

        assertThat(resolved).contains(new AdminAuditActionResolver.Resolved(
                AdminAuditAction.VIEW_USER, AdminAuditTargetType.USER, 42L));
    }

    @Test
    @DisplayName("방 활동(채팅·영상·질문)·최종선택 조회는 VIEW_ROOM_ACTIVITY, 그 외 방 조회는 VIEW_ROOM")
    void givenRoomPaths_whenResolve_thenActivityDistinguished() {
        Map<String, String> vars = Map.of("roomId", "107");

        assertThat(AdminAuditActionResolver.resolve("GET", P + "/rooms/{roomId}/activities", vars, 200).get().action())
                .isEqualTo(AdminAuditAction.VIEW_ROOM_ACTIVITY);
        assertThat(AdminAuditActionResolver.resolve("GET", P + "/rooms/{roomId}/activity/summary", vars, 200).get().action())
                .isEqualTo(AdminAuditAction.VIEW_ROOM_ACTIVITY);
        assertThat(AdminAuditActionResolver.resolve("GET", P + "/rooms/{roomId}/selections", vars, 200).get().action())
                .isEqualTo(AdminAuditAction.VIEW_ROOM_ACTIVITY);
        var detail = AdminAuditActionResolver.resolve("GET", P + "/rooms/{roomId}", vars, 200).get();
        assertThat(detail.action()).isEqualTo(AdminAuditAction.VIEW_ROOM);
        assertThat(detail.targetType()).isEqualTo(AdminAuditTargetType.ROOM);
        assertThat(detail.targetId()).isEqualTo(107L);
        assertThat(AdminAuditActionResolver.resolve("GET", P + "/rooms", Map.of(), 200).get().action())
                .isEqualTo(AdminAuditAction.VIEW_ROOMS);
    }

    @Test
    @DisplayName("로그인은 응답 코드로 LOGIN/LOGIN_FAIL 을 가르고, 내 정보와 접속기록 API 자체는 기록하지 않는다")
    void givenAuthAndAuditPaths_whenResolve_thenLoginSplitAndExclusions() {
        assertThat(AdminAuditActionResolver.resolve("POST", P + "/auth/login", Map.of(), 200).get().action())
                .isEqualTo(AdminAuditAction.LOGIN);
        assertThat(AdminAuditActionResolver.resolve("POST", P + "/auth/login", Map.of(), 401).get().action())
                .isEqualTo(AdminAuditAction.LOGIN_FAIL);
        assertThat(AdminAuditActionResolver.resolve("GET", P + "/me", Map.of(), 200)).isEmpty();
        assertThat(AdminAuditActionResolver.resolve("GET", P + "/audit/logs", Map.of(), 200).get().action())
                .isEqualTo(AdminAuditAction.VIEW);
        assertThat(AdminAuditActionResolver.resolve("POST", P + "/audit/exports", Map.of(), 200)).isEmpty();
        assertThat(AdminAuditActionResolver.resolve("OPTIONS", P + "/users", Map.of(), 200)).isEmpty();
        assertThat(AdminAuditActionResolver.resolve("GET", "/actuator/health", Map.of(), 200)).isEmpty();
    }

    @Test
    @DisplayName("쓰기 메서드는 CREATE/UPDATE/DELETE 로, 신고 경로는 변수 이름과 무관하게 REPORT 대상으로 기록된다")
    void givenWriteMethodsAndReportPaths_whenResolve_thenMapped() {
        assertThat(AdminAuditActionResolver.resolve("POST", P + "/rooms", Map.of(), 200).get().action())
                .isEqualTo(AdminAuditAction.CREATE);
        assertThat(AdminAuditActionResolver.resolve("PATCH", P + "/admins/{id}", Map.of("id", "3"), 200).get())
                .isEqualTo(new AdminAuditActionResolver.Resolved(AdminAuditAction.UPDATE, AdminAuditTargetType.ADMIN, 3L));
        assertThat(AdminAuditActionResolver.resolve("DELETE",
                P + "/rooms/{roomId}/missions/{missionId}/announcements/{announcementId}/message",
                Map.of("roomId", "9", "missionId", "1", "announcementId", "2"), 200).get())
                .isEqualTo(new AdminAuditActionResolver.Resolved(AdminAuditAction.DELETE, AdminAuditTargetType.ROOM, 9L));
        assertThat(AdminAuditActionResolver.resolve("GET", P + "/reports/{reportId}", Map.of("reportId", "5"), 200).get())
                .isEqualTo(new AdminAuditActionResolver.Resolved(AdminAuditAction.VIEW_REPORT, AdminAuditTargetType.REPORT, 5L));
        assertThat(AdminAuditActionResolver.resolve("POST", P + "/reports/{id}/approve", Map.of("id", "5"), 200).get())
                .isEqualTo(new AdminAuditActionResolver.Resolved(AdminAuditAction.CREATE, AdminAuditTargetType.REPORT, 5L));
        assertThat(AdminAuditActionResolver.resolve("GET", P + "/reports", Map.of(), 200).get().action())
                .isEqualTo(AdminAuditAction.VIEW_REPORTS);
    }

    @Test
    @DisplayName("숫자가 아닌 path variable 이면 대상 종류만 남기고 id 는 비운다")
    void givenNonNumericId_whenResolve_thenTargetIdNull() {
        Optional<AdminAuditActionResolver.Resolved> resolved =
                AdminAuditActionResolver.resolve("GET", P + "/users/{userId}", Map.of("userId", "abc"), 400);

        assertThat(resolved.get().targetType()).isEqualTo(AdminAuditTargetType.USER);
        assertThat(resolved.get().targetId()).isNull();
    }
}
