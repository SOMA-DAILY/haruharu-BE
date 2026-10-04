package org.son.haruharu.admin.domain.dashboard.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/** 운영 대시보드 상단 KPI. IAP 검증실패 수는 백엔드가 실패를 추적하지 않아 제외한다. */
public record DashboardSummaryResponse(
        @Schema(description = "진행중(ACTIVE) 방 현황") ActiveRooms activeRooms,
        @Schema(description = "오늘(KST 00:00 이후) 가입 유저 수", example = "57") long todaySignups
) {
    public record ActiveRooms(
            @Schema(description = "진행중 방 총 수", example = "12") long total,
            @Schema(description = "일차(1~최종)별 방 수. key=일차, value=방 수", example = "{\"1\":5,\"2\":4,\"3\":3}")
            Map<Integer, Long> byDay
    ) {}
}
